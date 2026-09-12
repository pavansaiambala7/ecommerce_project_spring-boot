package com.jtspringproject.JtSpringProject.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Supplies a PostgreSQL instance with the pgvector extension to integration
 * tests.
 *
 * <p>If {@code SPRING_DATASOURCE_URL} is set, that database is used as-is. This
 * lets the suite run against a service container (or any already-provisioned
 * database) in environments where starting sibling containers is awkward, such
 * as a build that is itself running inside a container.
 *
 * <p>Otherwise a pgvector container is started once per JVM and shared by every
 * test class, rather than restarted for each one.
 */
public abstract class PostgresTestBase {

	private static final String EXTERNAL_URL = System.getenv("SPRING_DATASOURCE_URL");

	private static final PostgreSQLContainer<?> POSTGRES;

	static {
		if (usingExternalDatabase()) {
			POSTGRES = null;
		} else {
			POSTGRES = new PostgreSQLContainer<>(
					DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
							.withDatabaseName("ecommjava")
							.withUsername("postgres")
							.withPassword("postgres");
			POSTGRES.start();
		}
	}

	private static boolean usingExternalDatabase() {
		return EXTERNAL_URL != null && !EXTERNAL_URL.isBlank();
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		if (POSTGRES == null) {
			// application.properties already resolves the SPRING_DATASOURCE_*
			// environment variables; nothing to override.
			return;
		}
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}
}
