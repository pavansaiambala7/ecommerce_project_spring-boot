package com.jtspringproject.JtSpringProject.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.embedding.model}")
    private String embeddingModelName;

    @Value("${gemini.chat.model}")
    private String chatModelName;

    /**
     * Vector width, which must match the {@code vector(...)} column declared in
     * migration V2 and the pgvector store's configured dimension.
     */
    @Value("${pgvector.dimension:768}")
    private int dimension;

    /**
     * The embedding model is pinned to the same dimensionality as the database
     * column.
     *
     * <p>gemini-embedding-001 returns 3072 dimensions unless asked otherwise, so
     * without {@code outputDimensionality} every insert into a {@code vector(768)}
     * column would be rejected at runtime.
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .outputDimensionality(dimension)
                .build();
    }

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModelName)
                .temperature(0.7)
                .maxOutputTokens(1024)
                .build();
    }
}
