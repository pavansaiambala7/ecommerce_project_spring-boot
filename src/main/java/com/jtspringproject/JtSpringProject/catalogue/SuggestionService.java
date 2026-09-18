package com.jtspringproject.JtSpringProject.catalogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jtspringproject.JtSpringProject.dto.response.SuggestionResponse;

/**
 * Search-as-you-type: "ap" offers "apple", "apples", "apple iphone".
 *
 * <p>Reads the {@code search_term} materialized view from V14, never the product
 * table. A suggestion request arrives on every keystroke, and tokenising fifty
 * thousand product names that often is exactly the load that makes a search box
 * feel sluggish.
 *
 * <p>Two passes. Prefix matching comes first because it is what a shopper
 * expects while typing. If that finds too little and the input is long enough
 * to be a misspelling rather than a fragment, trigram similarity fills the rest
 * - "samsng" still offers "samsung". This is the pg_trgm extension the schema
 * has carried since V1 finally doing something.
 */
@Service
public class SuggestionService {

	private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

	public static final int MAX_LIMIT = 10;

	/** Below this, similarity matching is noise: two letters resemble everything. */
	private static final int MIN_FUZZY_LENGTH = 4;

	private final NamedParameterJdbcTemplate jdbc;

	public SuggestionService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<SuggestionResponse> suggest(String rawQuery, int limit) {
		String q = normalise(rawQuery);
		int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
		if (q.isEmpty()) {
			return List.of();
		}

		// Keyed by text so a term that is both a brand and a word ("apple")
		// appears once. Insertion order keeps the ranking.
		Map<String, SuggestionResponse> results = new LinkedHashMap<>();

		// A department match is offered first: "lap" -> "laptops" should lead to
		// the department page, not a text search for the word.
		// DISTINCT ON picks one row per term, preferring department > brand >
		// phrase > word, then the outer query ranks by how much each would find.
		String prefixSql = """
				SELECT term, category_id, category_name FROM (
				    SELECT DISTINCT ON (t.term) t.term, t.hits,
				           CASE WHEN t.kind = 'category' THEN t.category_id END AS category_id,
				           CASE WHEN t.kind = 'category' THEN c.name END AS category_name,
				           CASE t.kind WHEN 'category' THEN 0 WHEN 'brand' THEN 1
				                       WHEN 'phrase' THEN 2 ELSE 3 END AS kind_rank
				    FROM search_term t
				    LEFT JOIN category c ON c.category_id = t.category_id AND t.kind = 'category'
				    WHERE t.term LIKE :prefix ESCAPE '\\' AND t.hits > 0
				    ORDER BY t.term, kind_rank
				) ranked
				ORDER BY (category_id IS NOT NULL) DESC, hits DESC, length(term), term
				LIMIT :limit
				""";
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("prefix", escapeLike(q) + "%")
				.addValue("limit", size);
		jdbc.query(prefixSql, params, rs -> {
			String term = rs.getString("term");
			results.putIfAbsent(term, new SuggestionResponse(term,
					(Integer) rs.getObject("category_id"), rs.getString("category_name")));
		});

		if (results.size() < size && q.length() >= MIN_FUZZY_LENGTH) {
			String fuzzySql = """
					SELECT term, max(hits) AS hits, max(similarity(term, :q)) AS sim
					FROM search_term
					WHERE term % :q AND kind <> 'category' AND hits > 0
					GROUP BY term
					ORDER BY sim DESC, hits DESC
					LIMIT :limit
					""";
			jdbc.query(fuzzySql, new MapSqlParameterSource().addValue("q", q).addValue("limit", size), rs -> {
				if (results.size() < size) {
					String term = rs.getString("term");
					results.putIfAbsent(term, new SuggestionResponse(term, null, null));
				}
			});
		}

		return new ArrayList<>(results.values());
	}

	/**
	 * Rebuilds the suggestion vocabulary from the current catalogue.
	 *
	 * <p>CONCURRENTLY keeps the old suggestions readable while the new ones are
	 * computed, so typing into the search box never blocks on a refresh.
	 */
	public void refresh() {
		long start = System.currentTimeMillis();
		jdbc.getJdbcTemplate().execute("REFRESH MATERIALIZED VIEW CONCURRENTLY search_term");
		log.info("Refreshed search suggestions in {} ms", System.currentTimeMillis() - start);
	}

	/**
	 * Picks up products an administrator added or renamed since the last
	 * refresh. Imports refresh immediately; this is the backstop for everything
	 * else, and fifteen minutes stale is harmless for suggestions.
	 */
	@Scheduled(fixedDelayString = "${app.search.suggestion-refresh-ms:900000}",
			initialDelayString = "${app.search.suggestion-refresh-ms:900000}")
	public void scheduledRefresh() {
		try {
			refresh();
		} catch (Exception e) {
			log.warn("Scheduled suggestion refresh failed: {}", e.getMessage());
		}
	}

	/** Lower case, single spaces, no leading or trailing space, capped in length. */
	static String normalise(String raw) {
		if (raw == null) {
			return "";
		}
		String q = raw.toLowerCase().replaceAll("\\s+", " ").stripLeading();
		// A trailing space is meaningful mid-typing ("apple " should suggest
		// "apple iphone", not "apples"), so only leading space is removed.
		return q.length() > 60 ? q.substring(0, 60) : q;
	}

	/** User input goes into a LIKE pattern; its wildcards must match literally. */
	static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
