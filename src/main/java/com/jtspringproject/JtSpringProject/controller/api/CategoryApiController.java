package com.jtspringproject.JtSpringProject.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.ai.service.CatalogueSearchService;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.response.CategoryResponse;

/**
 * Read-only category listing for the storefront navigation.
 *
 * <p>Without this the frontend had to download the whole product catalogue and
 * derive the category list in JavaScript - workable at a hundred products,
 * impossible at fifty thousand.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryApiController {

	private final CatalogueSearchService catalogueSearchService;

	public CategoryApiController(CatalogueSearchService catalogueSearchService) {
		this.catalogueSearchService = catalogueSearchService;
	}

	/** Only categories that actually contain products, so the nav has no dead ends. */
	@GetMapping
	public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
		return ResponseEntity.ok(ApiResponse.success(catalogueSearchService.categories()));
	}
}
