package com.jtspringproject.JtSpringProject.catalogue;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jtspringproject.JtSpringProject.ai.service.CatalogueSearchService;
import com.jtspringproject.JtSpringProject.dto.request.CatalogueQuery;
import com.jtspringproject.JtSpringProject.dto.response.ProductResponse;
import com.jtspringproject.JtSpringProject.dto.response.StorefrontHomeResponse;
import com.jtspringproject.JtSpringProject.dto.response.StorefrontHomeResponse.Card;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Assembles the landing page: deal cards per department and a row of the
 * catalogue's best discounts.
 *
 * <p>Every number a card shows comes from the products on it. "Up to 62% off"
 * is the largest discount actually in that department and "Starting Rs 299" is
 * its cheapest in-stock item - a headline the catalogue cannot back is the kind
 * of detail that makes a shop look fake.
 *
 * <p>Cached briefly. This is the most requested page in the shop and its
 * content changes only when the catalogue does; imports evict the cache.
 */
@Service
public class StorefrontService {

	/** How a card picks its products and words its headline. */
	private enum Headline {
		/** "Up to N% off" - for departments where the discount is the story. */
		DISCOUNT,
		/** "Starting Rs N" - for departments where the price floor is. */
		STARTING_PRICE,
		/** The subtitle alone, for cards already defined by a price cap. */
		PLAIN
	}

	private record CardSpec(String key, String department, String subtitle, Headline headline,
			BigDecimal maxPrice, String sort) {
	}

	/**
	 * The landing page's cards, in display order. Keyed on department names from
	 * V14; a card whose department is missing or empty is simply not shown.
	 */
	private static final List<CardSpec> CARDS = List.of(
			new CardSpec("men", "Men's Fashion", "Deals for men", Headline.DISCOUNT, null, "discount"),
			new CardSpec("women", "Women's Fashion", "Women's fashion", Headline.STARTING_PRICE, null, "discount"),
			new CardSpec("mobiles", "Mobiles", "Smartphones under ₹15,000", Headline.PLAIN,
					new BigDecimal("15000"), "rating"),
			new CardSpec("laptops", "Laptops", "Laptops for work and study", Headline.DISCOUNT, null, "discount"),
			new CardSpec("electronics", "Electronics", "Headphones, speakers and more", Headline.DISCOUNT, null,
					"discount"),
			new CardSpec("home", "Home & Kitchen", "Upgrade your kitchen", Headline.STARTING_PRICE, null, "rating"),
			new CardSpec("grocery", "Grocery", "Fresh groceries", Headline.DISCOUNT, null, "discount"),
			new CardSpec("beauty", "Beauty & Personal Care", "Beauty picks under ₹499", Headline.PLAIN,
					new BigDecimal("499"), "rating"),
			new CardSpec("footwear", "Footwear", "Shoes and sandals", Headline.DISCOUNT, null, "discount"),
			new CardSpec("sports", "Sports & Outdoors", "Fitness essentials", Headline.STARTING_PRICE, null, "rating"),
			new CardSpec("toys", "Toys & Games", "Toys and games", Headline.DISCOUNT, null, "discount"),
			new CardSpec("books", "Books", "Books for every reader", Headline.STARTING_PRICE, null, "rating"));

	private static final int PRODUCTS_PER_CARD = 4;
	private static final int DEALS = 16;

	private final CatalogueSearchService catalogueSearchService;
	private final NamedParameterJdbcTemplate jdbc;

	private final Cache<String, StorefrontHomeResponse> cache = Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterWrite(Duration.ofMinutes(5))
			.build();

	public StorefrontService(CatalogueSearchService catalogueSearchService, NamedParameterJdbcTemplate jdbc) {
		this.catalogueSearchService = catalogueSearchService;
		this.jdbc = jdbc;
	}

	public StorefrontHomeResponse home() {
		return cache.get("home", k -> build());
	}

	/** Called after the catalogue changes in bulk. */
	public void evict() {
		cache.invalidateAll();
	}

	StorefrontHomeResponse build() {
		Map<String, Integer> departmentIds = new LinkedHashMap<>();
		jdbc.query("SELECT category_id, name FROM category", new MapSqlParameterSource(),
				rs -> {
					departmentIds.putIfAbsent(rs.getString("name"), rs.getInt("category_id"));
				});

		List<Card> cards = new ArrayList<>();
		for (CardSpec spec : CARDS) {
			Integer departmentId = departmentIds.get(spec.department());
			if (departmentId == null) {
				continue;
			}
			Card card = card(spec, departmentId);
			if (card != null) {
				cards.add(card);
			}
		}

		// Today's deals: the biggest genuine discounts on well-reviewed products
		// that can actually be bought right now.
		CatalogueQuery deals = new CatalogueQuery();
		deals.setInStockOnly(true);
		deals.setMinDiscount(10);
		deals.setSort("discount");
		deals.setSize(DEALS);
		List<ProductResponse> dealItems = catalogueSearchService.search(deals).items();

		return new StorefrontHomeResponse(cards, dealItems);
	}

	private Card card(CardSpec spec, int departmentId) {
		CatalogueQuery query = new CatalogueQuery();
		query.setCategoryId(departmentId);
		query.setInStockOnly(true);
		query.setMaxPrice(spec.maxPrice());
		query.setSort(spec.sort());
		// More than the card shows, so products with a photograph can be
		// preferred: a card of four grey placeholders sells nothing.
		query.setSize(PRODUCTS_PER_CARD * 5);

		CatalogueSearchService.Page page = catalogueSearchService.search(query);
		if (page.items().size() < PRODUCTS_PER_CARD) {
			// A card with one lonely product looks broken, not sparse.
			return null;
		}
		List<ProductResponse> photographed = page.items().stream()
				.filter(p -> p.getImage() != null && !p.getImage().contains("placehold.co"))
				.limit(PRODUCTS_PER_CARD)
				.toList();
		List<ProductResponse> products = photographed.size() == PRODUCTS_PER_CARD
				? photographed
				: page.items().subList(0, PRODUCTS_PER_CARD);

		Map<String, String> link = new LinkedHashMap<>();
		link.put("categoryId", String.valueOf(departmentId));
		link.put("sort", spec.sort());
		if (spec.maxPrice() != null) {
			link.put("maxPrice", spec.maxPrice().toPlainString());
		}

		String headline = switch (spec.headline()) {
			case DISCOUNT -> {
				int best = maxDiscount(departmentId);
				// "Up to 3% off" is not a deal worth a headline.
				yield best >= 10 ? "Up to " + best + "% off" : spec.subtitle();
			}
			case STARTING_PRICE -> {
				BigDecimal floor = minPrice(departmentId);
				yield floor == null ? spec.subtitle() : "Starting ₹" + rupees(floor);
			}
			case PLAIN -> spec.subtitle();
		};
		String subtitle = headline.equals(spec.subtitle()) ? null : spec.subtitle();
		return new Card(spec.key(), headline, subtitle, link, products);
	}

	private int maxDiscount(int departmentId) {
		Integer best = jdbc.queryForObject("""
				SELECT coalesce(max(discount_percent), 0) FROM product
				WHERE quantity > 0 AND category_id IN (
				    SELECT category_id FROM category WHERE category_id = :id OR parent_id = :id)
				""", new MapSqlParameterSource("id", departmentId), Integer.class);
		return best == null ? 0 : best;
	}

	private BigDecimal minPrice(int departmentId) {
		return jdbc.queryForObject("""
				SELECT min(price) FROM product
				WHERE quantity > 0 AND category_id IN (
				    SELECT category_id FROM category WHERE category_id = :id OR parent_id = :id)
				""", new MapSqlParameterSource("id", departmentId), BigDecimal.class);
	}

	private static String rupees(BigDecimal amount) {
		NumberFormat format = NumberFormat.getNumberInstance(new Locale("en", "IN"));
		format.setMaximumFractionDigits(0);
		return format.format(amount);
	}
}
