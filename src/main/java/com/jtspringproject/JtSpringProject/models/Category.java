package com.jtspringproject.JtSpringProject.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
public class Category {
	@Id
	@Column(name = "category_id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	private String name;

	/**
	 * The department this one sits under, or null for a top-level department.
	 * Held as an id rather than an association: nothing loads a category's
	 * parent through JPA, and the tree is assembled in SQL.
	 */
	@Column(name = "parent_id")
	private Integer parentId;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder = 1000;

	/** Shown in the navigation bar under the search box. */
	@Column(nullable = false)
	private boolean featured;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getParentId() {
		return parentId;
	}

	public void setParentId(Integer parentId) {
		this.parentId = parentId;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public boolean isFeatured() {
		return featured;
	}

	public void setFeatured(boolean featured) {
		this.featured = featured;
	}
}
