package com.jtspringproject.JtSpringProject.ai.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jtspringproject.JtSpringProject.dto.request.CatalogueQuery;
import com.jtspringproject.JtSpringProject.dto.response.CategoryResponse;
import com.jtspringproject.JtSpringProject.dto.response.CategoryTreeResponse;
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

	/**
	 * Cosine distance past which a vector match is not a match at all.
	 *
	 * <p>An ANN search always returns its nearest neighbours, however far away
	 * they are. While a catalogue is still being embedded that is glaring: with
	 * only the grocery seed embedded, a search for "saree" was answered with
	 * Motichoor Ladoo, the nearest thing that happened to have a vector.
	 *
	 * <p>Measured against this catalogue with gemini-embedding-001: a real match
	 * lands at 0.27-0.31 ("fresh apples" to Fresh Red Apples), while unrelated
	 * products sit at 0.41-0.43 ("saree" to Motichoor Ladoo). The default splits
	 * the two, and is a property so it can be retuned without a rebuild.
	 */
	private final double maxVectorDistance;

	/**
	 * Full-text matching requires every word: "budget android phone" finds
	 * nothing, because no product description contains all three.
	 */
	private static final String TSQUERY_ALL = "websearch_to_tsquery('english', :q)";

	/**
	 * The same query with the words joined by OR instead. Used only as a second
	 * attempt when requiring every word found nothing, so precise searches keep
	 * their precision and vague ones still return something. Rewriting the
	 * parsed query rather than the raw text keeps user input out of the tsquery
	 * grammar.
	 */
	private static final String TSQUERY_ANY =
			"replace(websearch_to_tsquery('english', :q)::text, '&', '|')::tsquery";

	/** Every column {@link #mapProduct} reads, shared by all listing queries. */
	private static final String PRODUCT_COLUMNS = """
			p.product_id, p.name, p.description, p.image, p.price, p.mrp, p.discount_percent,
			p.quantity, p.weight, p.brand, p.rating, p.rating_count,
			c.category_id, c.name AS category_name""";

	private final NamedParameterJdbcTemplate jdbc;
	private final EmbeddingService embeddingService;

	public CatalogueSearchService(NamedParameterJdbcTemplate jdbc, EmbeddingService embeddingService,
			@org.springframework.beans.factory.annotation.Value("${app.search.max-vector-distance:0.40}") double maxVectorDistance) {
		this.jdbc = jdbc;
		this.embeddingService = embeddingService;
		this.maxVectorDistance = maxVectorDistance;
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

		if (!hasText) {
			List<ProductResponse> items = jdbc.query(browseSql(query.getSort()), params,
					CatalogueSearchService::mapProduct);
			return new Page(items, countMatches(query, null, null), page, size);
		}

		params.addValue("q", query.getQ());
		float[] queryVector = embedQueryOrNull(query.getQ());
		boolean hasVector = queryVector != null;
		if (hasVector) {
			params.addValue("queryVector", toVectorLiteral(queryVector));
			jdbc.getJdbcTemplate().execute("SET LOCAL hnsw.ef_search = " + EF_SEARCH);
		}

		// Full-text matching requires every word, so "cotton shirt for office"
		// matches nothing. When that happens, match any word instead - which is
		// what a shopper typing a whole sentence meant. Decided before the search
		// rather than by retrying an empty one, because with a vector side even
		// one distant neighbour would look like a hit and suppress the retry.
		String tsquery = hasKeywordMatch(query.getQ()) ? TSQUERY_ALL : TSQUERY_ANY;
		List<ProductResponse> items = find(query, params, tsquery, hasVector);
		String vectorLiteral = hasVector ? (String) params.getValue("queryVector") : null;
		return new Page(items, countMatches(query, tsquery, vectorLiteral), page, size);
	}

	private List<ProductResponse> find(CatalogueQuery query, MapSqlParameterSource params, String tsquery,
			boolean hasVector) {
		String sql = hasVector ? hybridSql(query.getSort(), tsquery) : lexicalSql(query.getSort(), tsquery);
		return jdbc.query(sql, params, CatalogueSearchService::mapProduct);
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

	/**
	 * The department tree with product counts, for the navigation bar and the
	 * department menu. Departments with nothing in them are left out, so no
	 * link leads to an empty page.
	 */
	@Transactional(readOnly = true)
	public List<CategoryTreeResponse> categoryTree() {
		record Row(int id, String name, Integer parentId, boolean featured, long ownCount) {
		}
		List<Row> rows = jdbc.query("""
				SELECT c.category_id, c.name, c.parent_id, c.featured,
				       (SELECT count(*) FROM product p WHERE p.category_id = c.category_id) AS own_count
				FROM category c
				ORDER BY c.sort_order, c.name
				""", new MapSqlParameterSource(), (rs, n) -> new Row(
				rs.getInt("category_id"), rs.getString("name"),
				(Integer) rs.getObject("parent_id"), rs.getBoolean("featured"), rs.getLong("own_count")));

		List<CategoryTreeResponse> tree = new ArrayList<>();
		for (Row top : rows) {
			if (top.parentId() != null) {
				continue;
			}
			List<CategoryTreeResponse> children = rows.stream()
					.filter(child -> Objects.equals(child.parentId(), top.id()) && child.ownCount() > 0)
					.map(child -> new CategoryTreeResponse(child.id(), child.name(), child.featured(),
							child.ownCount(), List.of()))
					.toList();
			long total = top.ownCount() + children.stream().mapToLong(CategoryTreeResponse::productCount).sum();
			if (total > 0) {
				tree.add(new CategoryTreeResponse(top.id(), top.name(), top.featured(), total, children));
			}
		}
		return tree;
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

	/**
	 * Nearest neighbours by vector distance.
	 *
	 * <p>The inner query keeps ORDER BY ... LIMIT directly on the distance
	 * operator, which is the only form pgvector can answer from the HNSW index;
	 * anything wrapping the distance first silently falls back to a sequential
	 * scan. The distance cutoff is therefore applied outside it, on the rows the
	 * index already returned.
	 */
	private String vecCte() {
		return """
				vec AS (
				    SELECT product_id, row_number() OVER (ORDER BY distance) AS rank
				    FROM (SELECT product_id, embedding <=> CAST(:queryVector AS vector) AS distance
				          FROM product
				          WHERE embedding IS NOT NULL
				          ORDER BY embedding <=> CAST(:queryVector AS vector)
				          LIMIT %d) ranked
				    WHERE distance < %s
				)""".formatted(CANDIDATE_POOL, maxVectorDistance);
	}

	/** Best full-text matches. The tsquery is a subquery because only a function call may be aliased in FROM. */
	private static String lexCte(String tsquery) {
		return """
				lex AS (
				    SELECT product_id, row_number() OVER (ORDER BY ts_rank(search_vector, q.tsq) DESC) AS rank
				    FROM product, (SELECT %s) AS q(tsq)
				    WHERE search_vector @@ q.tsq
				    LIMIT %d
				)""".formatted(tsquery, CANDIDATE_POOL);
	}

	private String hybridSql(String sort, String tsquery) {
		return """
				WITH %s,
				%s
				SELECT %s,
				       coalesce(1.0 / (%d + vec.rank), 0) + coalesce(1.0 / (%d + lex.rank), 0) AS score
				FROM product p
				LEFT JOIN category c ON c.category_id = p.category_id
				LEFT JOIN vec ON vec.product_id = p.product_id
				LEFT JOIN lex ON lex.product_id = p.product_id
				WHERE (vec.product_id IS NOT NULL OR lex.product_id IS NOT NULL) AND %s
				ORDER BY %s
				LIMIT :limit OFFSET :offset
				""".formatted(vecCte(), lexCte(tsquery), PRODUCT_COLUMNS, RRF_K, RRF_K,
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
	private String lexicalSql(String sort, String tsquery) {
		return """
				SELECT %s,
				       ts_rank(p.search_vector, %s) AS score
				FROM product p
				LEFT JOIN category c ON c.category_id = p.category_id
				WHERE p.search_vector @@ %s AND %s
				ORDER BY %s
				LIMIT :limit OFFSET :offset
				""".formatted(PRODUCT_COLUMNS, tsquery, tsquery, filterPredicate(true), orderBy(sort, true));
	}

	private String browseSql(String sort) {
		return """
				SELECT %s, 0 AS score
				FROM product p
				LEFT JOIN category c ON c.category_id = p.category_id
				WHERE %s
				ORDER BY %s
				LIMIT :limit OFFSET :offset
				""".formatted(PRODUCT_COLUMNS, filterPredicate(true), orderBy(sort, false));
	}

	/**
	 * How many products the search found.
	 *
	 * <p>Counts the same fused candidate set the results come from, including
	 * the vector side. Counting only keyword matches reported "0 results" above
	 * a page full of them. For a text search the number is bounded by
	 * {@link #CANDIDATE_POOL}, which is what "showing the best 200 matches"
	 * means; a filtered browse counts the whole catalogue exactly.
	 */
	private long countMatches(CatalogueQuery query, String tsquery, String queryVectorLiteral) {
		MapSqlParameterSource params = filterParams(query);
		String sql;
		if (tsquery == null) {
			sql = "SELECT count(*) FROM product p WHERE " + filterPredicate(true);
		} else {
			params.addValue("q", query.getQ());
			if (queryVectorLiteral != null) {
				// The same vector the search used; embedding the query again
				// here would spend a second paid call on one number.
				params.addValue("queryVector", queryVectorLiteral);
			}
			sql = queryVectorLiteral != null
					? """
							WITH %s,
							%s
							SELECT count(*) FROM product p
							LEFT JOIN vec ON vec.product_id = p.product_id
							LEFT JOIN lex ON lex.product_id = p.product_id
							WHERE (vec.product_id IS NOT NULL OR lex.product_id IS NOT NULL) AND %s
							""".formatted(vecCte(), lexCte(tsquery), filterPredicate(true))
					: """
							WITH %s
							SELECT count(*) FROM product p
							JOIN lex ON lex.product_id = p.product_id WHERE %s
							""".formatted(lexCte(tsquery), filterPredicate(true));
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
		// A department includes the departments under it: "Fashion" lists
		// men's, women's and footwear, not only products filed directly on the
		// parent row (of which there are none).
		return """
				(CAST(:categoryId AS integer) IS NULL
				 OR %scategory_id IN (SELECT sub.category_id FROM category sub
				                      WHERE sub.category_id = CAST(:categoryId AS integer)
				                         OR sub.parent_id = CAST(:categoryId AS integer)))
				AND (CAST(:minPrice AS numeric) IS NULL OR %sprice >= CAST(:minPrice AS numeric))
				AND (CAST(:maxPrice AS numeric) IS NULL OR %sprice <= CAST(:maxPrice AS numeric))
				AND (CAST(:inStockOnly AS boolean) = false OR %squantity > 0)
				AND (CAST(:brand AS text) IS NULL OR %sbrand = CAST(:brand AS text))
				AND (CAST(:minDiscount AS integer) IS NULL OR %sdiscount_percent >= CAST(:minDiscount AS integer))
				""".formatted(p, p, p, p, p, p);
	}

	private MapSqlParameterSource filterParams(CatalogueQuery query) {
		return new MapSqlParameterSource()
				.addValue("categoryId", query.getCategoryId())
				.addValue("minPrice", query.getMinPrice())
				.addValue("maxPrice", query.getMaxPrice())
				.addValue("inStockOnly", query.isInStockOnly())
				.addValue("brand", query.getBrand())
				.addValue("minDiscount", query.getMinDiscount());
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
			// Biggest saving first; among equal discounts, the better-reviewed
			// product, which is what a "deals" page is for.
			case "discount" -> "p.discount_percent DESC, p.rating DESC NULLS LAST, p.product_id";
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
		dto.setMrp(rs.getBigDecimal("mrp"));
		dto.setDiscountPercent(rs.getInt("discount_percent"));
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

	/**
	 * Whether any product's text matches the query as keywords. Used where a
	 * vector search's "nearest" results would otherwise be shown for a query
	 * that has nothing to do with the catalogue.
	 */
	@Transactional(readOnly = true)
	public boolean hasKeywordMatch(String query) {
		Boolean match = jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM product
				               WHERE search_vector @@ websearch_to_tsquery('english', :q))
				""", new MapSqlParameterSource("q", query), Boolean.class);
		return Boolean.TRUE.equals(match);
	}

	/** Formats the top matches as plain text for the chat assistant's context. */
	@Transactional(readOnly = true)
	public String buildSearchContext(String query) {
		CatalogueQuery q = new CatalogueQuery();
		q.setQ(query);
		q.setSize(5);
		List<ProductResponse> results = search(q).items();
		return results.isEmpty() ? "No matching products found." : formatForContext(results);
	}

	/**
	 * Products as the assistant sees them. Prices are rupees with Indian digit
	 * grouping, and the id is included so a reply can be matched back to the
	 * products it mentions.
	 */
	public static String formatForContext(List<ProductResponse> products) {
		List<String> lines = new ArrayList<>();
		java.text.NumberFormat rupees = java.text.NumberFormat.getNumberInstance(new java.util.Locale("en", "IN"));
		for (ProductResponse p : products) {
			String offer = p.getDiscountPercent() > 0
					? " (MRP Rs %s, %d%% off)".formatted(rupees.format(p.getMrp()), p.getDiscountPercent())
					: "";
			lines.add("- %s: Rs %s%s, %s%s%s".formatted(
					p.getName(), rupees.format(p.getPrice()), offer,
					p.isInStock() ? "in stock" : "out of stock",
					p.getRating() == null ? "" : ", rated " + p.getRating() + "/5",
					p.getCategory() == null ? "" : ", in " + p.getCategory().getName()));
		}
		return String.join("\n", lines);
	}
}
