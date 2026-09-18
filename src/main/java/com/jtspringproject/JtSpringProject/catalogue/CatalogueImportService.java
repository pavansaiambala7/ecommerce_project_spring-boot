package com.jtspringproject.JtSpringProject.catalogue;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.sql.DataSource;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;

/**
 * Bulk-loads products from the CSV that {@code tools/generate_catalogue.py}
 * writes, over HTTP.
 *
 * <p>This exists so that loading a catalogue does not need shell access to the
 * database host. The previous route - copy the file to the server, copy it into
 * the container, run psql - failed on the first step whenever the operator's
 * machine was not in the SSH security group.
 *
 * <p>Rows stream straight into PostgreSQL's COPY, so a 50,000-product file is
 * never held in memory, and the whole import is one transaction: it lands
 * completely or not at all. Re-importing the same file updates rows by
 * {@code external_id} instead of duplicating them.
 */
@Service
public class CatalogueImportService {

	private static final Logger log = LoggerFactory.getLogger(CatalogueImportService.class);

	/** A 50,000-product CSV is about 12 MB; this leaves room without being unbounded. */
	static final long MAX_BYTES = 200L * 1024 * 1024;

	static final Set<String> REQUIRED_COLUMNS = Set.of("external_id", "name", "price", "category_name");
	static final Set<String> KNOWN_COLUMNS = Set.of("external_id", "name", "description", "image", "price",
			"mrp", "quantity", "weight", "brand", "rating", "rating_count", "category_name");

	public record ImportResult(long rowsRead, long inserted, long updated, long skippedUnknownDepartment,
			long skippedInvalid, long awaitingEmbedding) {
	}

	private final DataSource dataSource;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final SuggestionService suggestionService;
	private final StorefrontService storefrontService;

	public CatalogueImportService(DataSource dataSource, JdbcTemplate jdbc, TransactionTemplate transactions,
			SuggestionService suggestionService, StorefrontService storefrontService) {
		this.dataSource = dataSource;
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.suggestionService = suggestionService;
		this.storefrontService = storefrontService;
	}

	/**
	 * @param body CSV, optionally gzip-compressed (detected from its first bytes,
	 *             so a client does not have to label it correctly)
	 */
	public ImportResult importCsv(InputStream body) throws IOException {
		PushbackInputStream in = new PushbackInputStream(body, 2);
		// The limit applies to the decompressed text, which is what reaches the
		// database: a few megabytes of gzip can otherwise expand without bound.
		InputStream csv = new LimitedInputStream(isGzip(in) ? new GZIPInputStream(in, 64 * 1024) : in, MAX_BYTES);
		List<String> columns = readHeader(csv);

		long start = System.currentTimeMillis();
		ImportResult result = transactions.execute(status -> {
			try {
				return load(csv, columns);
			} catch (SQLException | IOException e) {
				// COPY reports the offending line, which is exactly what an
				// administrator fixing a malformed file needs to see.
				throw new BusinessRuleException("Import failed: " + rootMessage(e));
			}
		});

		// Outside the transaction: these must see the committed rows.
		jdbc.execute("ANALYZE product");
		suggestionService.refresh();
		storefrontService.evict();

		log.info("Catalogue import: {} rows read, {} inserted, {} updated, {} unknown department, "
				+ "{} invalid, in {} ms", result.rowsRead(), result.inserted(), result.updated(),
				result.skippedUnknownDepartment(), result.skippedInvalid(), System.currentTimeMillis() - start);
		return result;
	}

	private ImportResult load(InputStream csv, List<String> columns) throws SQLException, IOException {
		Connection connection = DataSourceUtils.getConnection(dataSource);

		jdbc.execute("""
				CREATE TEMP TABLE catalogue_staging (
				    external_id   text,
				    name          text,
				    description   text,
				    image         text,
				    price         numeric(12,2),
				    mrp           numeric(12,2),
				    quantity      int,
				    weight        int,
				    brand         text,
				    rating        numeric(2,1),
				    rating_count  int,
				    category_name text
				) ON COMMIT DROP
				""");

		// Column names were checked against KNOWN_COLUMNS, so building the list
		// from them cannot inject SQL.
		CopyManager copy = new CopyManager(connection.unwrap(BaseConnection.class));
		long rowsRead = copy.copyIn("COPY catalogue_staging (" + String.join(", ", columns)
				+ ") FROM STDIN WITH (FORMAT csv)", csv);

		int invalid = jdbc.update("""
				DELETE FROM catalogue_staging
				WHERE external_id IS NULL OR external_id = '' OR length(external_id) > 64
				   OR name IS NULL OR name = '' OR price IS NULL OR price < 0
				   OR (mrp IS NOT NULL AND mrp < price)
				   OR (rating IS NOT NULL AND (rating < 0 OR rating > 5))
				""");

		// Filing a product under a department that does not exist would either
		// fail the whole import or leave it uncategorised; skipping it and
		// saying so is more useful than both.
		int unknownDepartment = jdbc.update("""
				DELETE FROM catalogue_staging s
				WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.name = s.category_name)
				""");

		// A re-import keeps a product's vector unless the text it was embedded
		// from changed. Clearing it unconditionally would make re-running the
		// same file cost another five hundred embedding calls for nothing.
		long[] counts = jdbc.queryForObject("""
				WITH upserted AS (
				    INSERT INTO product (external_id, name, description, image, price, mrp, quantity,
				                         weight, brand, rating, rating_count, category_id)
				    SELECT DISTINCT ON (s.external_id)
				           s.external_id, left(s.name, 255), s.description, left(s.image, 255), s.price, s.mrp,
				           coalesce(s.quantity, 0), coalesce(s.weight, 0), left(s.brand, 120),
				           s.rating, coalesce(s.rating_count, 0), c.category_id
				    FROM catalogue_staging s
				    JOIN category c ON c.name = s.category_name
				    ORDER BY s.external_id
				    ON CONFLICT (external_id) DO UPDATE SET
				        name         = EXCLUDED.name,
				        description  = EXCLUDED.description,
				        image        = EXCLUDED.image,
				        price        = EXCLUDED.price,
				        mrp          = EXCLUDED.mrp,
				        quantity     = EXCLUDED.quantity,
				        weight       = EXCLUDED.weight,
				        brand        = EXCLUDED.brand,
				        rating       = EXCLUDED.rating,
				        rating_count = EXCLUDED.rating_count,
				        category_id  = EXCLUDED.category_id,
				        embedding    = CASE
				            WHEN product.name IS DISTINCT FROM EXCLUDED.name
				              OR product.description IS DISTINCT FROM EXCLUDED.description
				              OR product.brand IS DISTINCT FROM EXCLUDED.brand
				              OR product.category_id IS DISTINCT FROM EXCLUDED.category_id
				            THEN NULL ELSE product.embedding END
				    RETURNING (xmax = 0) AS inserted
				)
				SELECT count(*) FILTER (WHERE inserted), count(*) FILTER (WHERE NOT inserted) FROM upserted
				""", (rs, n) -> new long[] { rs.getLong(1), rs.getLong(2) });

		Long awaiting = jdbc.queryForObject("SELECT count(*) FROM product WHERE embedding IS NULL", Long.class);
		return new ImportResult(rowsRead, counts[0], counts[1], unknownDepartment, invalid,
				awaiting == null ? 0 : awaiting);
	}

	// ------------------------------------------------------------------ helpers

	private static boolean isGzip(PushbackInputStream in) throws IOException {
		byte[] magic = new byte[2];
		int read = in.readNBytes(magic, 0, 2);
		if (read > 0) {
			in.unread(magic, 0, read);
		}
		return read == 2 && (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b;
	}

	/**
	 * Reads and validates the header line, leaving the stream at the first row.
	 * Columns are matched by name, so files with or without the newer columns
	 * (such as {@code mrp}) both load.
	 */
	static List<String> readHeader(InputStream csv) throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int b;
		while ((b = csv.read()) != -1 && b != '\n') {
			if (line.size() > 4096) {
				throw new BusinessRuleException("The first line is not a CSV header.");
			}
			line.write(b);
		}
		String header = line.toString(StandardCharsets.UTF_8).replace("﻿", "").strip();
		if (header.isEmpty()) {
			throw new BusinessRuleException("The file is empty.");
		}

		Set<String> columns = new LinkedHashSet<>();
		for (String raw : header.split(",")) {
			String column = raw.strip().replace("\"", "").toLowerCase();
			if (!KNOWN_COLUMNS.contains(column)) {
				throw new BusinessRuleException("Unknown column '" + column + "'. Expected some of: "
						+ String.join(", ", KNOWN_COLUMNS) + ".");
			}
			if (!columns.add(column)) {
				throw new BusinessRuleException("Column '" + column + "' appears twice.");
			}
		}
		List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !columns.contains(c)).sorted().toList();
		if (!missing.isEmpty()) {
			throw new BusinessRuleException("Missing required column(s): " + String.join(", ", missing) + ".");
		}
		return new ArrayList<>(columns);
	}

	private static String rootMessage(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
		return message.length() > 500 ? message.substring(0, 500) : message;
	}

	/** Refuses to read past a byte limit, so a runaway upload cannot fill the disk or the database. */
	static final class LimitedInputStream extends FilterInputStream {
		private final long limit;
		private long count;

		LimitedInputStream(InputStream in, long limit) {
			super(in);
			this.limit = limit;
		}

		@Override
		public int read() throws IOException {
			int b = super.read();
			if (b != -1) {
				advance(1);
			}
			return b;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int n = super.read(buffer, offset, length);
			if (n > 0) {
				advance(n);
			}
			return n;
		}

		private void advance(long n) throws IOException {
			count += n;
			if (count > limit) {
				throw new IOException("Upload exceeds " + (limit / (1024 * 1024)) + " MB.");
			}
		}
	}
}
