package com.jtspringproject.JtSpringProject.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jtspringproject.JtSpringProject.models.Category;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.services.productService;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

@ExtendWith(MockitoExtension.class)
class RagProductSearchServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private productService productService;

    @InjectMocks
    private RagProductSearchService ragSearchService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        Category cat = new Category();
        cat.setName("Fruits");

        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Red Apple");
        testProduct.setPrice(3);
        testProduct.setDescription("Juicy apple");
        testProduct.setCategory(cat);
    }

    @Test
    void searchProducts_shouldReturnMatchedProducts() {
        Embedding queryEmbed = Embedding.from(new float[768]);
        when(embeddingService.embedText("apples")).thenReturn(queryEmbed);

        Metadata metadata = new Metadata();
        metadata.put("productId", "1");
        TextSegment segment = TextSegment.from("Product: Red Apple", metadata);

        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.92, "match-id", queryEmbed, segment);
        EmbeddingSearchResult<TextSegment> searchResult = new EmbeddingSearchResult<>(List.of(match));

        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(searchResult);
        when(productService.getProduct(1)).thenReturn(testProduct);

        List<RagProductSearchService.SearchResult> results = ragSearchService.searchProducts("apples");

        assertEquals(1, results.size());
        assertEquals("Red Apple", results.get(0).getProduct().getName());
        assertEquals(0.92, results.get(0).getScore());
    }

    @Test
    void buildSearchContext_shouldFormatProductDetails() {
        Embedding queryEmbed = Embedding.from(new float[768]);
        when(embeddingService.embedText("apples")).thenReturn(queryEmbed);

        Metadata metadata = new Metadata();
        metadata.put("productId", "1");
        TextSegment segment = TextSegment.from("Product: Red Apple", metadata);

        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.90, "match-id", queryEmbed, segment);
        when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of(match)));
        when(productService.getProduct(1)).thenReturn(testProduct);

        String context = ragSearchService.buildSearchContext("apples");

        assertTrue(context.contains("Red Apple"));
        assertTrue(context.contains("Score: 0.90"));
    }
}
