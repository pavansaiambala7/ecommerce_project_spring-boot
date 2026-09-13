package com.jtspringproject.JtSpringProject.controller.api;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.ProductRequest;
import com.jtspringproject.JtSpringProject.dto.response.ProductResponse;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.services.categoryService;
import com.jtspringproject.JtSpringProject.services.productService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    /**
     * Sortable columns. An arbitrary sortBy value reached Spring Data directly and
     * produced a 500 from PropertyReferenceException for any unknown property.
     */
    private static final Set<String> SORTABLE = Set.of("id", "name", "price", "quantity", "weight");

    private final productService productService;
    private final categoryService categoryService;

    public ProductApiController(productService productService, categoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        List<ProductResponse> products = productService.getProducts().stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/paged")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProductsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        if (!SORTABLE.contains(sortBy)) {
            throw new BusinessRuleException("Cannot sort by '" + sortBy + "'. Allowed values: " + SORTABLE + ".");
        }
        if (page < 0) {
            throw new BusinessRuleException("Page index cannot be negative.");
        }
        if (size < 1 || size > 100) {
            throw new BusinessRuleException("Page size must be between 1 and 100.");
        }

        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Page<ProductResponse> products = productService.getProductsPaged(PageRequest.of(page, size, sort))
                .map(ProductResponse::from);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable int id) {
        Product product = productService.requireProduct(id);
        return ResponseEntity.ok(ApiResponse.success(ProductResponse.from(product)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        Product created = productService.addProduct(toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", ProductResponse.from(created)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable int id,
            @Valid @RequestBody ProductRequest request) {
        Product updated = productService.updateProduct(id, toEntity(request));
        return ResponseEntity.ok(
                ApiResponse.success("Product updated successfully", ProductResponse.from(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable int id) {
        boolean deleted = productService.deleteProduct(id);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
        }
        return ResponseEntity.notFound().build();
    }

    private Product toEntity(ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setImage(request.getImage());
        product.setPrice(request.getPrice());
        product.setQuantity(request.getQuantity());
        product.setWeight(request.getWeight());
        product.setCategory(categoryService.requireCategory(request.getCategoryId()));
        return product;
    }
}
