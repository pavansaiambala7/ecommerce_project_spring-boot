package com.jtspringproject.JtSpringProject.dto.response;

import java.util.List;

/**
 * A department and the departments under it, for the storefront navigation.
 *
 * @param productCount products in this department including its children, so
 *                     "Fashion (6,800)" counts men's, women's and footwear
 */
public record CategoryTreeResponse(
		int id,
		String name,
		boolean featured,
		long productCount,
		List<CategoryTreeResponse> children) {
}
