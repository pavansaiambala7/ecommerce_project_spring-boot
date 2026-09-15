package com.jtspringproject.JtSpringProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for creating or renaming a category. */
public class CategoryRequest {

	@NotBlank(message = "Category name is required")
	@Size(max = 255, message = "Category name must be at most 255 characters")
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
