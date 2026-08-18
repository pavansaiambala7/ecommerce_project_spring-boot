package com.jtspringproject.JtSpringProject.ai.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

@Configuration
public class PgVectorConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${pgvector.dimension:768}")
    private int dimension;

    @Value("${pgvector.table:product_embeddings}")
    private String tableName;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // Extract host, port, database from JDBC URL
        // jdbc:postgresql://localhost:5432/ecommjava
        String cleanUrl = jdbcUrl.replace("jdbc:postgresql://", "");
        String[] hostPortDb = cleanUrl.split("[:/]");
        String host = hostPortDb[0];
        int port = Integer.parseInt(hostPortDb[1]);
        String database = hostPortDb[2];

        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(username)
                .password(password)
                .table(tableName)
                .dimension(dimension)
                .createTable(false) // Flyway manages the table
                .build();
    }
}
