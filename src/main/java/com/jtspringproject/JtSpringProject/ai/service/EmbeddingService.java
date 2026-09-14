package com.jtspringproject.JtSpringProject.ai.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.models.Product;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Keeps {@code product.embedding} in step with the catalogue.
 *
 * <p>Vectors live on the product row rather than in a side table. That is what
 * lets a search filter by price or category and still rank by vector distance
 * in one statement, and it removes the follow-up query per hit that the old
 * store-based lookup needed.
 *
 * <p>Two properties matter at catalogue scale:
 *
 * <ul>
 * <li><b>Batched.</b> Gemini embeds up to 100 segments per call. Embedding one
 * product per request turns a fifty-thousand product index into fifty thousand
 * round trips; batching makes it five hundred.</li>
 * <li><b>Resumable.</b> {@link #embedMissing()} only touches rows with no
 * vector, so a run interrupted by a quota limit or a restart continues where it
 * stopped. The previous implementation truncated the whole index before it
 * began, which meant any failure left the catalogue unsearchable.</li>
 * </ul>
 */
@Service
public class EmbeddingService {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

	/** Gemini's batchEmbedContents ceiling, and what LangChain4j batches to. */
	private static final int BATCH_SIZE = 100;

	private final EmbeddingModel embeddingModel;
	private final EmbeddingModel queryEmbeddingModel;
	private final JdbcTemplate jdbc;

	public EmbeddingService(EmbeddingModel embeddingModel,
			@Qualifier("queryEmbeddingModel") EmbeddingModel queryEmbeddingModel,
			JdbcTemplate jdbc) {
		this.embeddingModel = embeddingModel;
		this.queryEmbeddingModel = queryEmbeddingModel;
		this.jdbc = jdbc;
	}

	/**
	 * A product's identity and the text that represents it for embedding.
	 * Package-private so tests can drive the batching logic directly.
	 */
	record Row(int id, String text) {
	}

	/**
	 * Embeds every product that has no vector yet.
	 *
	 * @return how many products were embedded
	 */
	public int embedMissing() {
		return embed("SELECT p.product_id, p.name, p.description, p.brand, p.quantity, p.price, p.weight, "
				+ "c.name AS category_name FROM product p "
				+ "LEFT JOIN category c ON c.category_id = p.category_id "
				+ "WHERE p.embedding IS NULL");
	}

	/**
	 * Re-embeds the entire catalogue, including products that already have a
	 * vector. Needed after changing the embedding model or the text template,
	 * since vectors from different models are not comparable.
	 */
	public int reindexAll() {
		return embed("SELECT p.product_id, p.name, p.description, p.brand, p.quantity, p.price, p.weight, "
				+ "c.name AS category_name FROM product p "
				+ "LEFT JOIN category c ON c.category_id = p.category_id");
	}

	private int embed(String selectSql) {
		List<Row> rows = jdbc.query(selectSql, (rs, n) -> new Row(
				rs.getInt("product_id"),
				buildProductText(
						rs.getString("name"),
						rs.getString("description"),
						rs.getString("brand"),
						rs.getString("category_name"),
						rs.getBigDecimal("price") == null ? "" : rs.getBigDecimal("price").toPlainString(),
						rs.getInt("weight"),
						rs.getInt("quantity") > 0)));

		if (rows.isEmpty()) {
			log.info("No products need embedding");
			return 0;
		}

		int embedded = 0;
		for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
			List<Row> batch = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
			try {
				embedded += embedBatch(batch);
			} catch (Exception e) {
				// One bad batch must not abandon the rest. Because this only
				// ever writes vectors for rows it successfully embedded, a
				// later run picks up whatever this one missed.
				log.error("Batch starting at offset {} failed ({} products skipped)", start, batch.size(), e);
			}
		}

		log.info("Embedded {}/{} products", embedded, rows.size());
		return embedded;
	}

	private int embedBatch(List<Row> batch) {
		List<TextSegment> segments = batch.stream().map(r -> TextSegment.from(r.text())).toList();
		List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

		List<Object[]> args = new ArrayList<>(batch.size());
		for (int i = 0; i < batch.size(); i++) {
			args.add(new Object[] { toVectorLiteral(embeddings.get(i).vector()), batch.get(i).id() });
		}

		// Cast in SQL rather than binding a vector type: the JDBC driver has no
		// mapping for pgvector, so the value travels as text.
		jdbc.batchUpdate("UPDATE product SET embedding = CAST(? AS vector) WHERE product_id = ?", args);
		return batch.size();
	}

	/**
	 * Embeds a search query, using the RETRIEVAL_QUERY task type rather than the
	 * RETRIEVAL_DOCUMENT one used for indexing.
	 */
	public float[] embedQuery(String text) {
		return queryEmbeddingModel.embed(text).content().vector();
	}

	/** Re-embeds one product after it is created or edited. */
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	public void embedProduct(Product product) {
		String text = buildProductText(
				product.getName(),
				product.getDescription(),
				product.getBrand(),
				product.getCategory() == null ? null : product.getCategory().getName(),
				product.getPrice() == null ? "" : product.getPrice().toPlainString(),
				product.getWeight(),
				product.getQuantity() > 0);
		Embedding embedding = embeddingModel.embed(TextSegment.from(text)).content();
		jdbc.update("UPDATE product SET embedding = CAST(? AS vector) WHERE product_id = ?",
				toVectorLiteral(embedding.vector()), product.getId());
		log.debug("Embedded product {} (id={})", product.getName(), product.getId());
	}

	/** How many products still have no vector. Surfaced by the reindex endpoint. */
	public int countMissing() {
		Integer missing = jdbc.queryForObject(
				"SELECT count(*) FROM product WHERE embedding IS NULL", Integer.class);
		return missing == null ? 0 : missing;
	}

	/**
	 * The text a product is embedded as.
	 *
	 * <p>Products are never chunked. Chunking exists to fit documents that
	 * exceed a model's context window, and a product record is a few dozen
	 * tokens; splitting one across several vectors would scatter its identity
	 * so that no single vector represented the whole item. One product, one
	 * vector, built from the fields a shopper would actually search on.
	 */
	private String buildProductText(String name, String description, String brand,
			String category, String price, int weight, boolean inStock) {
		StringBuilder sb = new StringBuilder(256);
		sb.append("Product: ").append(name);
		if (brand != null && !brand.isBlank()) {
			sb.append(". Brand: ").append(brand);
		}
		if (category != null && !category.isBlank()) {
			sb.append(". Category: ").append(category);
		}
		if (description != null && !description.isBlank()) {
			sb.append(". Description: ").append(description);
		}
		sb.append(". Price: ").append(price);
		sb.append(". Weight: ").append(weight).append("g");
		sb.append(inStock ? ". In stock." : ". Out of stock.");
		return sb.toString();
	}

	private static String toVectorLiteral(float[] vector) {
		StringBuilder sb = new StringBuilder(vector.length * 8 + 2).append('[');
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(vector[i]);
		}
		return sb.append(']').toString();
	}
}
