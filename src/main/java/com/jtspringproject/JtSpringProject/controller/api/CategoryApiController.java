package com.jtspringproject.JtSpringProject.controller.api;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.ai.service.CatalogueSearchService;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.CategoryRequest;
import com.jtspringproject.JtSpringProject.dto.response.CategoryResponse;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.services.categoryService;

import jakarta.validation.Valid;

/**
 * Departments, for the storefront navigation and for administration.
 *
 * <p>Reads are public: a shopper has to see what is for sale before deciding to
 * create an account. Writes require ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryApiController {

	private final CatalogueSearchService catalogueSearchService;
	private final categoryService categoryService;

	public CategoryApiController(CatalogueSearchService catalogueSearchService,
			categoryService categoryService) {
		this.catalogueSearchService = catalogueSearchService;
		this.categoryService = categoryService;
	}

	/** Only categories that contain products, so the storefront nav has no dead ends. */
	@GetMapping
	public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
		return ResponseEntity.ok(ApiResponse.success(catalogueSearchService.categories()));
	}

	/**
	 * Every category, including empty ones.
	 *
	 * <p>Separate from the public listing because administration needs to see a
	 * category before anything has been filed under it - otherwise a newly
	 * created department would vanish until its first product existed.
	 */
	@GetMapping("/all")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
		List<CategoryResponse> all = categoryService.getCategories().stream()
				.map(CategoryResponse::from)
				.toList();
		return ResponseEntity.ok(ApiResponse.success(all));
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
		CategoryResponse created = CategoryResponse.from(categoryService.addCategory(request.getName()));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Category created", created));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<CategoryResponse>> rename(@PathVariable int id,
			@Valid @RequestBody CategoryRequest request) {
		CategoryResponse updated = CategoryResponse.from(categoryService.updateCategory(id, request.getName()));
		return ResponseEntity.ok(ApiResponse.success("Category updated", updated));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable int id) {
		// Products reference their category, so removing one that is still in
		// use hits a foreign key constraint. That surfaces as a generic data
		// integrity message, which tells an administrator nothing about what to
		// do next - translate it into the actual remedy.
		try {
			if (!categoryService.deleteCategory(id)) {
				throw new BusinessRuleException("Category " + id + " does not exist.");
			}
		} catch (DataIntegrityViolationException e) {
			throw new BusinessRuleException(
					"This department still has products. Move or delete them first.");
		}
		return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
	}
}
