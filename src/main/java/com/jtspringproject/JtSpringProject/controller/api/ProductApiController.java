package com.jtspringproject.JtSpringProject.controller.api;

import java.math.BigDecimal;
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

import com.jtspringproject.JtSpringProject.ai.service.CatalogueSearchService;
import com.jtspringproject.JtSpringProject.catalogue.SuggestionService;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.CatalogueQuery;
import com.jtspringproject.JtSpringProject.dto.request.ProductRequest;
import com.jtspringproject.JtSpringProject.dto.response.FacetResponse;
import com.jtspringproject.JtSpringProject.dto.response.ProductResponse;
import com.jtspringproject.JtSpringProject.dto.response.SuggestionResponse;
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
    private final CatalogueSearchService catalogueSearchService;
    private final SuggestionService suggestionService;

    public ProductApiController(productService productService, categoryService categoryService,
            CatalogueSearchService catalogueSearchService, SuggestionService suggestionService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.catalogueSearchService = catalogueSearchService;
        this.suggestionService = suggestionService;
    }

    /**
     * Search-as-you-type completions for the search box. Public and cheap: it
     * reads a precomputed vocabulary, never embeds anything.
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<SuggestionResponse>>> suggest(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponse.success(suggestionService.suggest(q, limit)));
    }

    /**
     * @deprecated Returns the entire catalogue in one response, which stops
     *             being viable past a few thousand products. Use
     *             {@code /api/products/search}, which filters and pages in the
     *             database.
     */
    @Deprecated
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        List<ProductResponse> products = productService.getProducts().stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    /**
     * Browse or search the catalogue.
     *
     * <p>One endpoint covers both: with {@code q} it ranks by hybrid relevance,
     * without it returns a plain filtered listing. Filters and sorting are
     * relational operations applied in SQL either way - a price range or a
     * "low to high" ordering is an exact constraint that vector similarity
     * cannot express, so it never belongs in the retrieval step.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<CatalogueSearchService.Page>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String brand,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam(required = false) Integer minDiscount,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {

        if (page < 0) {
            throw new BusinessRuleException("page must not be negative.");
        }
        if (size < 1 || size > 100) {
            throw new BusinessRuleException("size must be between 1 and 100.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessRuleException("minPrice must not exceed maxPrice.");
        }
        if (minDiscount != null && (minDiscount < 0 || minDiscount > 100)) {
            throw new BusinessRuleException("minDiscount must be between 0 and 100.");
        }

        CatalogueQuery query = new CatalogueQuery();
        query.setQ(q);
        query.setCategoryId(categoryId);
        query.setMinPrice(minPrice);
        query.setMaxPrice(maxPrice);
        query.setBrand(brand);
        query.setInStockOnly(inStockOnly);
        query.setMinDiscount(minDiscount);
        query.setSort(sort);
        query.setPage(page);
        query.setSize(size);

        return ResponseEntity.ok(ApiResponse.success(catalogueSearchService.search(query)));
    }

    /** Category counts and price range for the current filters, for the sidebar. */
    @GetMapping("/facets")
    public ResponseEntity<ApiResponse<FacetResponse>> facets(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly) {

        CatalogueQuery query = new CatalogueQuery();
        query.setCategoryId(categoryId);
        query.setMinPrice(minPrice);
        query.setMaxPrice(maxPrice);
        query.setInStockOnly(inStockOnly);

        return ResponseEntity.ok(ApiResponse.success(catalogueSearchService.facets(query)));
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
        // Checked here with a clear message; the database would also refuse it,
        // but as an anonymous constraint violation.
        if (request.getMrp() != null && request.getPrice() != null
                && request.getMrp().compareTo(request.getPrice()) < 0) {
            throw new BusinessRuleException("MRP cannot be lower than the selling price.");
        }
        Product product = new Product();
        product.setMrp(request.getMrp());
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
