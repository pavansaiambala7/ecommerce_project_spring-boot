package com.jtspringproject.JtSpringProject.ai.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jtspringproject.JtSpringProject.dto.request.CatalogueQuery;
import com.jtspringproject.JtSpringProject.dto.response.CategoryResponse;
import com.jtspringproject.JtSpringProject.dto.response.FacetResponse;
import com.jtspringproject.JtSpringProject.dto.response.ProductResponse;

/**
 * Browse and search the catalogue: filters, sorting, pagination, facet counts,
 * and hybrid keyword + semantic relevance.
 *
 * <p>This is deliberately native SQL rather than JPA or LangChain4j's
 * {@code EmbeddingStore}, for reasons that only show up at scale:
 *
 * <ul>
 * <li><b>The ANN index is only usable in one query shape.</b> pgvector picks an
 * HNSW index for {@code ORDER BY embedding <=> $1 LIMIT k} and nothing else.
 * The EmbeddingStore this replaces computed the distance inside a CTE and
 * ordered on the derived column, which forced a sequential scan over every
 * vector on every search - unnoticeable at a hundred products, hopeless at
 * fifty thousand.</li>
 * <li><b>Filtering and retrieval have to happen together.</b> With vectors on
 * the product row, one statement can restrict by category, price and stock and
 * still rank by distance. When vectors sat in their own table, the filters were
 * simply not reachable from the search.</li>
 * <li><b>No N+1.</b> The row carries the product, so results need no follow-up
 * query per hit.</li>
 * </ul>
 *
 * <p>Relevance fuses two rankings with Reciprocal Rank Fusion. Embeddings
 * generalise well but blur rare tokens, so a search for an exact model name can
 * rank the actual product below generic neighbours; full-text matching is the
 * opposite, exact but literal. RRF combines them on rank position alone, which
 * sidesteps the fact that a cosine distance and a {@code ts_rank} score are not
 * comparable quantities and would need arbitrary normalisation to add up.
 */
@Service
public class CatalogueSearchService {

	private static final Logger log = LoggerFactory.getLogger(CatalogueSearchService.class);

	/**
	 * Rows pulled from each ranking before fusion. Larger than any page the UI
	 * shows, because a filter applied after the ANN scan can discard most of
	 * what it returned; over-fetching keeps a full page available.
	 */
	private static final int CANDIDATE_POOL = 200;

	/**
	 * RRF damping constant. 60 is the value from the original TREC work and is
	 * the usual default: large enough that the top few ranks do not dominate,
	 * small enough that deep results still contribute little.
	 */
	private static final int RRF_K = 60;

	/**
	 * Size of the HNSW candidate list at query time. Higher means better recall
	 * and more latency. Set per statement rather than globally so ordinary
	 * non-vector queries are unaffected.
	 */
	private static final int EF_SEARCH = 100;

	private static final int MAX_PAGE_SIZE = 100;

	private final NamedParameterJdbcTemplate jdbc;
	private final EmbeddingService embeddingService;

	public CatalogueSearchService(NamedParameterJdbcTemplate jdbc, EmbeddingService embeddingService) {
		this.jdbc = jdbc;
		this.embeddingService = embeddingService;
	}

	/** One page of results plus the total for pagination controls. */
	public record Page(List<ProductResponse> items, long totalItems, int page, int size) {
		/**
		 * Explicitly annotated: Jackson serialises a record from its components,
		 * and a derived method without a {@code getX} name is otherwise left out
		 * of the JSON entirely, leaving the storefront with no page count.
		 */
		@JsonProperty("totalPages")
		public int totalPages() {
			return size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
		}
	}

	@Transactional(readOnly = true)
	public Page search(CatalogueQuery query) {
		int size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);
		int page = Math.max(query.getPage(), 0);

		boolean hasText = query.getQ() != null && !query.getQ().isBlank();
		MapSqlParameterSource params = filterParams(query)
				.addValue("limit", size)
				.addValue("offset", (long) page * size);

		String sql;
		if (hasText) {
			params.addValue("q", query.getQ());
			float[] queryVector = embedQueryOrNull(query.getQ());
			if (queryVector != null) {
				params.addValue("queryVector", toVectorLiteral(queryVector));
				jdbc.getJdbcTemplate().execute("SET LOCAL hnsw.ef_search = " + EF_SEARCH);
				sql = hybridSql(query.getSort());
			} else {
				sql = lexicalSql(query.getSort());
			}
		} else {
			sql = browseSql(query.getSort());
		}

		List<ProductResponse> items = jdbc.query(sql, params, CatalogueSearchService::mapProduct);
		long total = countMatches(query, hasText);
		return new Page(items, total, page, size);
	}

	/**
	 * Counts per category and price band for the current filters, so the UI can
	 * show "Electronics (1,284)" without a request per facet.
	 *
	 * <p>Facets intentionally ignore the text query: a shopper narrowing by
	 * category expects to see every category available under their filters, not
	 * only those surviving the current search terms.
	 */
	@Transactional(readOnly = true)
	public FacetResponse facets(CatalogueQuery query) {
		MapSqlParameterSource params = filterParams(query);

		// Aliased predicate: this query joins product and category, and both
		// carry a category_id, so an unqualified reference is ambiguous.
		String categorySql = """
				SELECT c.category_id, c.name, count(*) AS hits
				FROM product p JOIN category c ON c.category_id = p.category_id
				WHERE %s
				GROUP BY c.category_id, c.name
				ORDER BY hits DESC, c.name
				""".formatted(filterPredicate(true));

		List<FacetResponse.CategoryFacet> categories = jdbc.query(categorySql, params,
				(rs, row) -> new FacetResponse.CategoryFacet(
						rs.getInt("category_id"), rs.getString("name"), rs.getLong("hits")));

		String priceSql = """
				SELECT min(p.price) AS min_price, max(p.price) AS max_price
				FROM product p WHERE %s
				""".formatted(filterPredicate(true));

		Map<String, BigDecimal> range = jdbc.queryForObject(priceSql, params, (rs, row) -> {
			Map<String, BigDecimal> m = new LinkedHashMap<>();
			m.put("min", rs.getBigDecimal("min_price"));
			m.put("max", rs.getBigDecimal("max_price"));
			return m;
		});

		return new FacetResponse(categories,
				range == null ? null : range.get("min"),
				range == null ? null : range.get("max"));
	}

	/** Every category that currently has at least one product. */
	@Transactional(readOnly = true)
	public List<CategoryResponse> categories() {
		return jdbc.query("""
				SELECT c.category_id, c.name
				FROM category c
				WHERE EXISTS (SELECT 1 FROM product p WHERE p.category_id = c.category_id)
				ORDER BY c.name
				""", new MapSqlParameterSource(), (rs, row) -> {
			CategoryResponse dto = new CategoryResponse();
			dto.setId(rs.getInt("category_id"));
			dto.setName(rs.getString("name"));
			return dto;
		});
	}

	// ---------------------------------------------------------------- queries

	private String hybridSql(String sort) {
		// The vector CTE keeps ORDER BY ... LIMIT directly on the distance
		// operator, which is the only form pgvector can answer from the HNSW
		// index. Anything wrapping the distance first would silently fall back
		// to a sequential scan.
		return """
				WITH vec AS (
				    SELECT product_id, row_number() OVER () AS rank
				    FROM (SELECT product_id FROM product
				          WHERE embedding IS NOT NULL
				          ORDER BY embedding <=> CAST(:queryVector AS vector)
				          LIMIT %d) ranked
				),
				lex AS (
				    SELECT product_id, row_number() OVER (ORDER BY ts_rank(search_vector, q.tsq) DESC) AS rank
				    FROM product, websearch_to_tsquery('english', :q) AS q(tsq)
				    WHERE search_vector @@ q.tsq
				    LIMIT %d
				)
				SELECT p.product_id, p.name, p.description, p.image, p.price, p.quantity,
				       p.weight, p.brand, p.rating, p.rating_count,
				       c.category_id, c.name AS category_name,
				       coalesce(1.0 / (%d + vec.rank), 0) + coalesce(1.0 / (%d + lex.rank), 0) AS score
				FROM product p
				LEFT JOIN category c ON c.category_id = p.category_id
				LEFT JOIN vec ON vec.product_id = p.product_id
				LEFT JOIN lex ON lex.product_id = p.product_id
				WHERE (vec.product_id IS NOT NULL OR lex.product_id IS NOT NULL) AND %s
				ORDER BY %s
				LIMIT :limit OFFSET :offset
				""".formatted(CANDIDATE_POOL, CANDIDATE_POOL, RRF_K, RRF_K,
				filterPredicate(true), orderBy(sort, true));
	}

	/**
	 * Embeds the query, or returns null if the embedding service is unavailable.
	 *
	 * <p>Search must not depend on a third party being up. An expired key, an
	 * exhausted quota or a Gemini outage would otherwise take the entire
	 * catalogue search down with a 500; degrading to keyword-only search keeps
	 * the shop usable, just less clever.
	 */
	private float[] embedQueryOrNull(String text) {
		try {
			return embeddingService.embedQuery(text);
		} catch (Exception e) {
			log.warn("Query embedding failed, falling back to keyword-only search: {}", e.getMessage());
			return null;
		}
	}

	/** Keyword-only ranking, used when the embedding service is unreachable. */
	private String lexicalSql(String sort) {
		return """
				SELECT p.product_id, p.name, p.description, p.image, p.price, p.quantity,
				       p.weight, p.brand, p.rating, p.rating_count,
				       c.category_id, c.name AS category_name,
				       ts_rank(p.search_vector, websearch_to_tsquery('english', :q)) AS score
				FROM product p
				LEFT JOIN category c ON c.category_id = p.category_id
				WHERE p.search_vector @@ websearch_to_tsquery('english', :q) AND %s
				ORDER BY %s
				LIMIT :limit OFFSET :offset
				""".formatted(filterPredicate(true), orderBy(sort, true));
	}

	private String browseSql(String sort) {
		return """
				SELECT p.product_id, p.name, p.description, p.image, p.price, p.quantity,
				       p.weight, p.brand, p.rating, p.rating_count,
				       c.category_id, c.name AS category_name, 0 AS score
				FROM product p
				LEFT JOIN category c ON c.category_id = p.category_id
				WHERE %s
				ORDER BY %s
				LIMIT :limit OFFSET :offset
				""".formatted(filterPredicate(true), orderBy(sort, false));
	}

	private long countMatches(CatalogueQuery query, boolean hasText) {
		MapSqlParameterSource params = filterParams(query);
		String sql;
		if (hasText) {
			params.addValue("q", query.getQ());
			// Counting only the lexical side would undercount, and counting the
			// vector side means re-running the ANN scan. The pool is bounded by
			// CANDIDATE_POOL anyway, so the honest total for a text search is
			// "how many of the fused candidates survive the filters".
			sql = """
					WITH lex AS (
					    SELECT product_id FROM product, websearch_to_tsquery('english', :q) AS q(tsq)
					    WHERE search_vector @@ q.tsq LIMIT %d
					)
					SELECT count(*) FROM product p JOIN lex ON lex.product_id = p.product_id WHERE %s
					""".formatted(CANDIDATE_POOL, filterPredicate(true));
		} else {
			sql = "SELECT count(*) FROM product p WHERE " + filterPredicate(true);
		}
		Long total = jdbc.queryForObject(sql, params, Long.class);
		return total == null ? 0 : total;
	}

	// --------------------------------------------------------------- filtering

	/**
	 * Filters are plain relational predicates, not part of retrieval. Price
	 * ranges, stock and category are exact constraints that a vector similarity
	 * cannot express, so they belong in the WHERE clause regardless of whether
	 * the query had any search text.
	 */
	private String filterPredicate(boolean aliased) {
		String p = aliased ? "p." : "";
		// Every parameter is cast explicitly. An absent filter binds as an
		// untyped NULL, and Postgres cannot infer a type for a bare parameter
		// that only ever appears beside NULL - it rejects the statement with
		// "could not determine data type of parameter".
		return """
				(CAST(:categoryId AS integer) IS NULL OR %scategory_id = CAST(:categoryId AS integer))
				AND (CAST(:minPrice AS numeric) IS NULL OR %sprice >= CAST(:minPrice AS numeric))
				AND (CAST(:maxPrice AS numeric) IS NULL OR %sprice <= CAST(:maxPrice AS numeric))
				AND (CAST(:inStockOnly AS boolean) = false OR %squantity > 0)
				AND (CAST(:brand AS text) IS NULL OR %sbrand = CAST(:brand AS text))
				""".formatted(p, p, p, p, p);
	}

	private MapSqlParameterSource filterParams(CatalogueQuery query) {
		return new MapSqlParameterSource()
				.addValue("categoryId", query.getCategoryId())
				.addValue("minPrice", query.getMinPrice())
				.addValue("maxPrice", query.getMaxPrice())
				.addValue("inStockOnly", query.isInStockOnly())
				.addValue("brand", query.getBrand());
	}

	/**
	 * Sort keys are mapped through a fixed whitelist and never interpolated from
	 * user input, since this string is concatenated into SQL.
	 */
	private String orderBy(String sort, boolean relevanceAvailable) {
		if (sort == null) {
			return relevanceAvailable ? "score DESC, p.product_id" : "p.product_id";
		}
		return switch (sort) {
			case "price_asc" -> "p.price ASC, p.product_id";
			case "price_desc" -> "p.price DESC, p.product_id";
			case "rating" -> "p.rating DESC NULLS LAST, p.rating_count DESC, p.product_id";
			case "name" -> "p.name ASC, p.product_id";
			case "relevance" -> relevanceAvailable ? "score DESC, p.product_id" : "p.product_id";
			default -> {
				log.debug("Unknown sort '{}', falling back to default ordering", sort);
				yield relevanceAvailable ? "score DESC, p.product_id" : "p.product_id";
			}
		};
	}

	// ----------------------------------------------------------------- mapping

	private static ProductResponse mapProduct(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		ProductResponse dto = new ProductResponse();
		dto.setId(rs.getInt("product_id"));
		dto.setName(rs.getString("name"));
		dto.setDescription(rs.getString("description"));
		dto.setImage(rs.getString("image"));
		dto.setPrice(rs.getBigDecimal("price"));
		dto.setQuantity(rs.getInt("quantity"));
		dto.setWeight(rs.getInt("weight"));
		dto.setBrand(rs.getString("brand"));
		dto.setRating(rs.getBigDecimal("rating"));
		dto.setRatingCount(rs.getInt("rating_count"));
		dto.setInStock(rs.getInt("quantity") > 0);

		int categoryId = rs.getInt("category_id");
		if (!rs.wasNull()) {
			CategoryResponse category = new CategoryResponse();
			category.setId(categoryId);
			category.setName(rs.getString("category_name"));
			dto.setCategory(category);
		}
		return dto;
	}

	/** pgvector accepts a bracketed list; the driver has no native float[] binding. */
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

	/** Formats the top matches as plain text for the chat assistant's context. */
	@Transactional(readOnly = true)
	public String buildSearchContext(String query) {
		CatalogueQuery q = new CatalogueQuery();
		q.setQ(query);
		q.setSize(5);
		List<ProductResponse> results = search(q).items();
		if (results.isEmpty()) {
			return "No matching products found.";
		}
		List<String> lines = new ArrayList<>();
		for (ProductResponse p : results) {
			lines.add("- %s%s: $%s (%s)%s".formatted(
					p.getBrand() == null ? "" : p.getBrand() + " ",
					p.getName(), p.getPrice(),
					p.isInStock() ? "in stock" : "out of stock",
					p.getCategory() == null ? "" : " [" + p.getCategory().getName() + "]"));
		}
		return String.join("\n", lines);
	}
}
