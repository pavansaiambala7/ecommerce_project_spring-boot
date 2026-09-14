package com.jtspringproject.JtSpringProject.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Counts and ranges describing what is available under the current filters, so
 * the storefront can render a sidebar without one request per facet.
 */
public class FacetResponse {

	public record CategoryFacet(int id, String name, long count) {
	}

	private List<CategoryFacet> categories;
	private BigDecimal minPrice;
	private BigDecimal maxPrice;

	public FacetResponse() {
	}

	public FacetResponse(List<CategoryFacet> categories, BigDecimal minPrice, BigDecimal maxPrice) {
		this.categories = categories;
		this.minPrice = minPrice;
		this.maxPrice = maxPrice;
	}

	public List<CategoryFacet> getCategories() {
		return categories;
	}

	public void setCategories(List<CategoryFacet> categories) {
		this.categories = categories;
	}

	public BigDecimal getMinPrice() {
		return minPrice;
	}

	public void setMinPrice(BigDecimal minPrice) {
		this.minPrice = minPrice;
	}

	public BigDecimal getMaxPrice() {
		return maxPrice;
	}

	public void setMaxPrice(BigDecimal maxPrice) {
		this.maxPrice = maxPrice;
	}
}
