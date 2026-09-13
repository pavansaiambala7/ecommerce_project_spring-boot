package com.jtspringproject.JtSpringProject.controller.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.ai.service.EmbeddingService;
import com.jtspringproject.JtSpringProject.ai.service.RagProductSearchService;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.response.SearchResultResponse;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    private static final int MAX_LIMIT = 50;

    private final RagProductSearchService ragSearchService;
    private final EmbeddingService embeddingService;

    public SearchApiController(RagProductSearchService ragSearchService,
                               EmbeddingService embeddingService) {
        this.ragSearchService = ragSearchService;
        this.embeddingService = embeddingService;
    }

    /**
     * AI-powered semantic product search.
     *
     * <p>Every call spends a paid embedding request, so the rate limit filter
     * applies a tighter quota to this path than to the rest of the API.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SearchResultResponse>>> searchProducts(
            @RequestParam @NotBlank @Size(max = 500) String q,
            @RequestParam(defaultValue = "5") int limit) {

        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessRuleException("limit must be between 1 and " + MAX_LIMIT + ".");
        }

        List<SearchResultResponse> response = ragSearchService.searchProducts(q, limit).stream()
                .map(SearchResultResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                "Found " + response.size() + " results", response));
    }

    /**
     * Rebuilds all product embeddings. Administrative: a full reindex issues one
     * paid embedding request per product.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindexProducts() {
        int count = embeddingService.embedAllProducts();
        Map<String, Object> result = new HashMap<>();
        result.put("productsIndexed", count);
        return ResponseEntity.ok(ApiResponse.success("Product embeddings reindexed", result));
    }
}
