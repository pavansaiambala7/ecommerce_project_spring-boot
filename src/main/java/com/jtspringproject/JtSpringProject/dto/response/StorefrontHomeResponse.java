package com.jtspringproject.JtSpringProject.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Everything the landing page shows, in one response.
 *
 * <p>One request rather than one per card: the landing page is the most
 * visited page in any shop, and eight round trips before it can paint would be
 * the slowest thing about it.
 */
public record StorefrontHomeResponse(List<Card> cards, List<ProductResponse> deals) {

	/**
	 * A tile such as "Up to 60% off | Men's fashion" with four products.
	 *
	 * @param headline built from the live data - the discount and starting
	 *                 price in the text are the real ones for these products,
	 *                 never marketing numbers that the catalogue does not back
	 * @param query    the browse filters that "See all" opens, as URL parameters
	 */
	public record Card(String key, String headline, String subtitle, Map<String, String> query,
			List<ProductResponse> products) {
	}
}
