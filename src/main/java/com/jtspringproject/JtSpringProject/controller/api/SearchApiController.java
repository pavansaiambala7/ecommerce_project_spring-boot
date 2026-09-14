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

import com.jtspringproject.JtSpringProject.ai.service.CatalogueSearchService;
import com.jtspringproject.JtSpringProject.ai.service.EmbeddingService;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.CatalogueQuery;
import com.jtspringproject.JtSpringProject.dto.response.ProductResponse;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    private static final int MAX_LIMIT = 50;

    private final CatalogueSearchService catalogueSearchService;
    private final EmbeddingService embeddingService;

    public SearchApiController(CatalogueSearchService catalogueSearchService,
                               EmbeddingService embeddingService) {
        this.catalogueSearchService = catalogueSearchService;
        this.embeddingService = embeddingService;
    }

    /**
     * Hybrid product search: keyword and semantic relevance fused.
     *
     * <p>Every call spends a paid embedding request for the query, so the rate
     * limit filter applies a tighter quota to this path than to the rest of the
     * API.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProducts(
            @RequestParam @NotBlank @Size(max = 500) String q,
            @RequestParam(defaultValue = "5") int limit) {

        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessRuleException("limit must be between 1 and " + MAX_LIMIT + ".");
        }

        CatalogueQuery query = new CatalogueQuery();
        query.setQ(q);
        query.setSize(limit);
        List<ProductResponse> response = catalogueSearchService.search(query).items();

        return ResponseEntity.ok(ApiResponse.success(
                "Found " + response.size() + " results", response));
    }

    /**
     * Embeds products that have no vector yet.
     *
     * <p>Incremental by default: products are embedded when created or edited,
     * so this only has work to do after a bulk import. Pass {@code full=true}
     * to re-embed everything, which is needed after changing the embedding
     * model or the text template, since vectors produced by different models
     * cannot be compared against each other.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindexProducts(
            @RequestParam(defaultValue = "false") boolean full) {

        int count = full ? embeddingService.reindexAll() : embeddingService.embedMissing();

        Map<String, Object> result = new HashMap<>();
        result.put("productsIndexed", count);
        result.put("stillMissing", embeddingService.countMissing());
        return ResponseEntity.ok(ApiResponse.success(
                full ? "Full reindex complete" : "Missing embeddings generated", result));
    }
}
