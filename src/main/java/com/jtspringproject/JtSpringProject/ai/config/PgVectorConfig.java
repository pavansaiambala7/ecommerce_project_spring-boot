package com.jtspringproject.JtSpringProject.ai.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

@Configuration
public class PgVectorConfig {

    private static final int DEFAULT_POSTGRES_PORT = 5432;

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
        JdbcTarget target = parseJdbcUrl(jdbcUrl);

        return PgVectorEmbeddingStore.builder()
                .host(target.host())
                .port(target.port())
                .database(target.database())
                .user(username)
                .password(password)
                .table(tableName)
                .dimension(dimension)
                .createTable(false) // Flyway manages the table
                .build();
    }

    /**
     * Extracts host, port and database from a JDBC URL.
     *
     * <p>This was {@code url.replace("jdbc:postgresql://", "").split("[:/]")},
     * which threw NumberFormatException when the port was omitted and folded any
     * query string into the database name.
     */
    static JdbcTarget parseJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            throw new IllegalArgumentException("Not a JDBC URL: " + jdbcUrl);
        }
        try {
            // Strip the "jdbc:" prefix so the remainder parses as a normal URI.
            URI uri = new URI(jdbcUrl.substring("jdbc:".length()));

            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("JDBC URL has no host: " + jdbcUrl);
            }

            int port = uri.getPort() == -1 ? DEFAULT_POSTGRES_PORT : uri.getPort();

            String path = uri.getPath();
            if (path == null || path.length() <= 1) {
                throw new IllegalArgumentException("JDBC URL has no database name: " + jdbcUrl);
            }
            // getPath() excludes the query string, so parameters cannot leak in.
            String database = path.substring(1);

            return new JdbcTarget(host, port, database);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed JDBC URL: " + jdbcUrl, e);
        }
    }

    record JdbcTarget(String host, int port, String database) {
    }
}
