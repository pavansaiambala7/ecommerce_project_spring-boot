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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private productService productService;

    @InjectMocks
    private EmbeddingService embeddingService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Fruits");

        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Fresh Apple");
        testProduct.setDescription("Juicy red apple");
        testProduct.setPrice(3);
        testProduct.setQuantity(50);
        testProduct.setCategory(category);
    }

    @Test
    void embedProduct_shouldGenerateAndStoreEmbedding() {
        float[] vector = new float[768];
        Embedding mockEmbedding = Embedding.from(vector);
        when(embeddingModel.embed(any(TextSegment.class))).thenReturn(Response.from(mockEmbedding));

        embeddingService.embedProduct(testProduct);

        verify(embeddingModel).embed(any(TextSegment.class));
        verify(embeddingStore).add(eq(mockEmbedding), any(TextSegment.class));
    }

    @Test
    void embedAllProducts_shouldProcessList() {
        float[] vector = new float[768];
        Embedding mockEmbedding = Embedding.from(vector);
        when(embeddingModel.embed(any(TextSegment.class))).thenReturn(Response.from(mockEmbedding));
        when(productService.getProducts()).thenReturn(List.of(testProduct));

        int count = embeddingService.embedAllProducts();

        assertEquals(1, count);
        verify(embeddingStore, times(1)).add(any(), any());
    }

    @Test
    void embedText_shouldReturnEmbeddingForQuery() {
        float[] vector = new float[768];
        Embedding mockEmbedding = Embedding.from(vector);
        when(embeddingModel.embed("organic fruits")).thenReturn(Response.from(mockEmbedding));

        Embedding result = embeddingService.embedText("organic fruits");

        assertNotNull(result);
        assertEquals(768, result.dimension());
    }
}
