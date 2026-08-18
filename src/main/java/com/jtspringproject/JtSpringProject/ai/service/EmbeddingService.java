package com.jtspringproject.JtSpringProject.ai.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.services.productService;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final productService productService;

    public EmbeddingService(EmbeddingModel embeddingModel,
                           EmbeddingStore<TextSegment> embeddingStore,
                           productService productService) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.productService = productService;
    }

    /**
     * Generate embedding for a single product and store it in pgvector.
     */
    public void embedProduct(Product product) {
        String text = buildProductText(product);
        TextSegment segment = TextSegment.from(text, buildMetadata(product));
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);
        log.info("Embedded product: {} (id={})", product.getName(), product.getId());
    }

    /**
     * Batch embed all products in the database.
     */
    public int embedAllProducts() {
        List<Product> products = productService.getProducts();
        int count = 0;

        for (Product product : products) {
            try {
                embedProduct(product);
                count++;
            } catch (Exception e) {
                log.error("Failed to embed product: {} (id={})", product.getName(), product.getId(), e);
            }
        }

        log.info("Embedded {}/{} products successfully", count, products.size());
        return count;
    }

    /**
     * Generate an embedding vector for a given text query.
     */
    public Embedding embedText(String text) {
        return embeddingModel.embed(text).content();
    }

    /**
     * Build a rich text representation of a product for embedding.
     */
    private String buildProductText(Product product) {
        StringBuilder sb = new StringBuilder();
        sb.append("Product: ").append(product.getName());

        if (product.getDescription() != null && !product.getDescription().isEmpty()) {
            sb.append(". Description: ").append(product.getDescription());
        }

        if (product.getCategory() != null && product.getCategory().getName() != null) {
            sb.append(". Category: ").append(product.getCategory().getName());
        }

        sb.append(". Price: $").append(product.getPrice());
        sb.append(". Weight: ").append(product.getWeight()).append("g");

        if (product.getQuantity() > 0) {
            sb.append(". In stock.");
        } else {
            sb.append(". Out of stock.");
        }

        return sb.toString();
    }

    /**
     * Build metadata for the embedding store entry.
     */
    private Metadata buildMetadata(Product product) {
        Metadata metadata = new Metadata();
        metadata.put("productId", String.valueOf(product.getId()));
        metadata.put("productName", product.getName());
        metadata.put("price", String.valueOf(product.getPrice()));
        if (product.getCategory() != null) {
            metadata.put("category", product.getCategory().getName());
        }
        return metadata;
    }
}
