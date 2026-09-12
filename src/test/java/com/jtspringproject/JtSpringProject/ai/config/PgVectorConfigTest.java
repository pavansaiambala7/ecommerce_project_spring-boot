package com.jtspringproject.JtSpringProject.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.jtspringproject.JtSpringProject.ai.config.PgVectorConfig.JdbcTarget;

/**
 * The previous implementation was {@code url.replace("jdbc:postgresql://", "")
 * .split("[:/]")}, which threw on a portless URL and folded query parameters
 * into the database name.
 */
class PgVectorConfigTest {

	@Test
	void parsesHostPortAndDatabase() {
		JdbcTarget target = PgVectorConfig.parseJdbcUrl("jdbc:postgresql://localhost:5432/ecommjava");

		assertEquals("localhost", target.host());
		assertEquals(5432, target.port());
		assertEquals("ecommjava", target.database());
	}

	@Test
	void defaultsThePortWhenOmitted() {
		JdbcTarget target = PgVectorConfig.parseJdbcUrl("jdbc:postgresql://db/ecommjava");

		assertEquals("db", target.host());
		assertEquals(5432, target.port());
		assertEquals("ecommjava", target.database());
	}

	@Test
	void ignoresQueryParameters() {
		JdbcTarget target = PgVectorConfig
				.parseJdbcUrl("jdbc:postgresql://localhost:5432/ecommjava?sslmode=require&ApplicationName=app");

		assertEquals("ecommjava", target.database());
	}

	@Test
	void handlesAContainerAssignedRandomPort() {
		JdbcTarget target = PgVectorConfig.parseJdbcUrl("jdbc:postgresql://localhost:49732/test?loggerLevel=OFF");

		assertEquals(49732, target.port());
		assertEquals("test", target.database());
	}

	@Test
	void rejectsMalformedUrls() {
		assertThrows(IllegalArgumentException.class, () -> PgVectorConfig.parseJdbcUrl(null));
		assertThrows(IllegalArgumentException.class, () -> PgVectorConfig.parseJdbcUrl("postgresql://h:5432/d"));
		assertThrows(IllegalArgumentException.class,
				() -> PgVectorConfig.parseJdbcUrl("jdbc:postgresql://localhost:5432"));
	}
}
