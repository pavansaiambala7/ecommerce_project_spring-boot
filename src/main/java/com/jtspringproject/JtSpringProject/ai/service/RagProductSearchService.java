package com.jtspringproject.JtSpringProject.ai.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.services.productService;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

@Service
public class RagProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagProductSearchService.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final double MIN_SCORE = 0.5;

    private final EmbeddingService embeddingService;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final productService productService;

    public RagProductSearchService(EmbeddingService embeddingService,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   productService productService) {
        this.embeddingService = embeddingService;
        this.embeddingStore = embeddingStore;
        this.productService = productService;
    }

    /**
     * Perform AI-powered semantic product search using RAG pipeline:
     * 1. Convert user query to embedding using Gemini
     * 2. Search pgvector for similar product embeddings (cosine similarity)
     * 3. Return matched products with relevance scores
     */
    public List<SearchResult> searchProducts(String userQuery) {
        return searchProducts(userQuery, DEFAULT_TOP_K);
    }

    public List<SearchResult> searchProducts(String userQuery, int topK) {
        log.info("RAG search for query: '{}' (topK={})", userQuery, topK);

        // Step 1: Embed the user's search query
        Embedding queryEmbedding = embeddingService.embedText(userQuery);

        // Step 2: Search pgvector for similar embeddings
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(MIN_SCORE)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // Step 3: Map results to products with scores
        List<SearchResult> results = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
            String productIdStr = match.embedded().metadata().getString("productId");
            if (productIdStr != null) {
                int productId = Integer.parseInt(productIdStr);
                Product product = productService.getProduct(productId);
                if (product != null) {
                    results.add(new SearchResult(product, match.score()));
                }
            }
        }

        log.info("RAG search returned {} results for query: '{}'", results.size(), userQuery);
        return results;
    }

    /**
     * Build context string from search results for LLM augmentation.
     */
    public String buildSearchContext(String userQuery) {
        List<SearchResult> results = searchProducts(userQuery);
        if (results.isEmpty()) {
            return "No relevant products found for the query.";
        }

        StringBuilder context = new StringBuilder("Relevant products found:\n");
        for (int i = 0; i < results.size(); i++) {
            Product p = results.get(i).getProduct();
            context.append(String.format("%d. %s - $%d (%s) - %s [Score: %.2f]\n",
                    i + 1,
                    p.getName(),
                    p.getPrice(),
                    p.getCategory() != null ? p.getCategory().getName() : "N/A",
                    p.getDescription(),
                    results.get(i).getScore()));
        }
        return context.toString();
    }

    /**
     * Search result wrapper with product and relevance score.
     */
    public static class SearchResult {
        private final Product product;
        private final double score;

        public SearchResult(Product product, double score) {
            this.product = product;
            this.score = score;
        }

        public Product getProduct() {
            return product;
        }

        public double getScore() {
            return score;
        }
    }
}
