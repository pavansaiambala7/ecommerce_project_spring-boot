package com.jtspringproject.JtSpringProject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.jtspringproject.JtSpringProject.support.PostgresTestBase;

/**
 * Boots the full application against a real PostgreSQL with pgvector, which also
 * exercises every Flyway migration and Hibernate's schema validation.
 */
@SpringBootTest
@ActiveProfiles("test")
class JtSpringProjectApplicationTests extends PostgresTestBase {

	@Test
	void contextLoads() {
		// Fails if any bean cannot be constructed, if a migration is broken, or if
		// an entity no longer matches the migrated schema.
	}

}
