package com.jtspringproject.JtSpringProject.controller.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jtspringproject.JtSpringProject.ai.service.EmbeddingService;
import com.jtspringproject.JtSpringProject.ai.service.RagProductSearchService;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.models.Product;

@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    private final RagProductSearchService ragSearchService;
    private final EmbeddingService embeddingService;

    public SearchApiController(RagProductSearchService ragSearchService,
                               EmbeddingService embeddingService) {
        this.ragSearchService = ragSearchService;
        this.embeddingService = embeddingService;
    }

    /**
     * AI-powered semantic product search using RAG pipeline.
     * Query is converted to embedding → pgvector cosine similarity → ranked results.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int limit) {

        List<RagProductSearchService.SearchResult> results = ragSearchService.searchProducts(q, limit);

        List<Map<String, Object>> response = results.stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("product", r.getProduct());
            item.put("relevanceScore", r.getScore());
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(
                "Found " + results.size() + " results for: " + q, response));
    }

    /**
     * Trigger re-indexing of all product embeddings.
     */
    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindexProducts() {
        int count = embeddingService.embedAllProducts();
        Map<String, Object> result = new HashMap<>();
        result.put("productsIndexed", count);
        return ResponseEntity.ok(ApiResponse.success("Product embeddings reindexed", result));
    }
}
