package com.jtspringproject.JtSpringProject.dto.request;

import java.math.BigDecimal;

/**
 * Filters, sorting and paging for a catalogue browse or search.
 *
 * <p>Every field is optional. With no {@code q} this describes a pure filtered
 * browse; with one it also ranks by hybrid relevance. Keeping both in a single
 * shape means the storefront can add or remove the search box without changing
 * which endpoint it talks to.
 */
public class CatalogueQuery {

	private String q;
	private Integer categoryId;
	private BigDecimal minPrice;
	private BigDecimal maxPrice;
	private String brand;
	private boolean inStockOnly;
	/** One of: relevance, price_asc, price_desc, rating, name. */
	private String sort;
	private int page = 0;
	private int size = 24;

	public String getQ() {
		return q;
	}

	public void setQ(String q) {
		this.q = q;
	}

	public Integer getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(Integer categoryId) {
		this.categoryId = categoryId;
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

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public boolean isInStockOnly() {
		return inStockOnly;
	}

	public void setInStockOnly(boolean inStockOnly) {
		this.inStockOnly = inStockOnly;
	}

	public String getSort() {
		return sort;
	}

	public void setSort(String sort) {
		this.sort = sort;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}
}
