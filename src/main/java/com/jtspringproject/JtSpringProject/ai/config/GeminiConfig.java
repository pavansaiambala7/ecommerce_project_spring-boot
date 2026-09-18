package com.jtspringproject.JtSpringProject.ai.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

    /** Comma-separated models tried in order when the primary one fails. */
    @Value("${gemini.chat.fallback-models:}")
    private String fallbackModelNames;

    /**
     * Gemini 2.5 and later "think" before answering, and those hidden tokens
     * count against this limit - a short reply measured about 600 of them. At
     * the old 1024 a longer answer could be cut off, or come back empty.
     */
    @Value("${gemini.chat.max-output-tokens:4096}")
    private int maxOutputTokens;

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
    @Primary
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .outputDimensionality(dimension)
                // Gemini's retrieval embeddings are asymmetric: a stored
                // document and the query that should find it are embedded with
                // different task types into the same space. Leaving this unset
                // treats both as generic text and measurably weakens ranking.
                // RETRIEVAL_DOCUMENT is correct here because this model is used
                // for indexing; the query side is embedded separately.
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
                .build();
    }

    /**
     * The query-side counterpart of {@link #embeddingModel()}.
     *
     * <p>Same model and dimensionality, different task type. Both must exist:
     * embedding a search query as if it were a product description puts it in
     * the wrong region of the space and costs recall.
     */
    @Bean
    @Qualifier("queryEmbeddingModel")
    public EmbeddingModel queryEmbeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .outputDimensionality(dimension)
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_QUERY)
                .build();
    }

    /**
     * The primary chat model with its fallbacks, so one retired or overloaded
     * model does not take the assistant down. See {@link FallbackChatModel}.
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        Set<String> names = new LinkedHashSet<>();
        names.add(chatModelName.strip());
        for (String name : fallbackModelNames.split(",")) {
            if (!name.isBlank()) {
                names.add(name.strip());
            }
        }

        List<FallbackChatModel.Candidate> candidates = new ArrayList<>();
        for (String name : names) {
            candidates.add(new FallbackChatModel.Candidate(name, GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(name)
                    .temperature(0.7)
                    .maxOutputTokens(maxOutputTokens)
                    .timeout(java.time.Duration.ofSeconds(45))
                    // Retries belong to the fallback: retrying a retired model
                    // three times only delays reaching one that works.
                    .maxRetries(1)
                    .build()));
        }
        return new FallbackChatModel(candidates);
    }
}
