package com.jtspringproject.JtSpringProject.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.jtspringproject.JtSpringProject.security.ApiErrorWriter;

class RateLimitFilterTest {

	private RateLimitProperties properties;
	private RateLimitFilter filter;

	@BeforeEach
	void setUp() {
		properties = new RateLimitProperties();
		properties.setEnabled(true);
		properties.setTiers(List.of(
				new RateLimitProperties.Tier("chat", List.of("/api/chat/**"), 2, Duration.ofMinutes(1)),
				new RateLimitProperties.Tier("api", List.of("/api/**"), 5, Duration.ofMinutes(1))));

		RateLimitService service = new RateLimitService(properties);
		// Built the way Spring Boot builds it, so the writer has the same modules
		// registered as in production - a bare ObjectMapper cannot serialise the
		// LocalDateTime on ApiResponse.
		filter = new RateLimitFilter(service, properties,
				new ApiErrorWriter(Jackson2ObjectMapperBuilder.json().build()));
	}

	private MockHttpServletResponse call(String uri, String clientIp) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
		request.setRequestURI(uri);
		request.setRemoteAddr(clientIp);
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

	@Test
	void allowsRequestsWithinTheQuota() throws Exception {
		assertEquals(200, call("/api/products", "10.0.0.1").getStatus());
		assertEquals(200, call("/api/products", "10.0.0.1").getStatus());
	}

	@Test
	void rejectsWithTooManyRequestsOnceExhausted() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertEquals(200, call("/api/products", "10.0.0.2").getStatus());
		}

		MockHttpServletResponse blocked = call("/api/products", "10.0.0.2");
		assertEquals(429, blocked.getStatus());
		assertTrue(blocked.getContentType().startsWith("application/json"));
		assertTrue(blocked.getContentAsString().contains("Rate limit exceeded"));
	}

	@Test
	void advertisesLimitAndRemainingHeaders() throws Exception {
		MockHttpServletResponse first = call("/api/products", "10.0.0.3");

		assertEquals("5", first.getHeader("X-RateLimit-Limit"));
		assertEquals("4", first.getHeader("X-RateLimit-Remaining"));
	}

	@Test
	void setsRetryAfterWhenBlocked() throws Exception {
		for (int i = 0; i < 5; i++) {
			call("/api/products", "10.0.0.4");
		}

		MockHttpServletResponse blocked = call("/api/products", "10.0.0.4");
		assertEquals("0", blocked.getHeader("X-RateLimit-Remaining"));
		assertNotNull(blocked.getHeader("Retry-After"));
		assertTrue(Long.parseLong(blocked.getHeader("Retry-After")) >= 1);
	}

	/** Buckets are per client, so one caller cannot throttle another. */
	@Test
	void tracksClientsIndependently() throws Exception {
		for (int i = 0; i < 5; i++) {
			call("/api/products", "10.0.0.5");
		}
		assertEquals(429, call("/api/products", "10.0.0.5").getStatus());
		assertEquals(200, call("/api/products", "10.0.0.6").getStatus());
	}

	/** The first matching tier wins, so the tighter chat quota applies there. */
	@Test
	void appliesTheMostSpecificTierFirst() throws Exception {
		assertEquals(200, call("/api/chat/send", "10.0.0.7").getStatus());
		assertEquals(200, call("/api/chat/send", "10.0.0.7").getStatus());
		assertEquals(429, call("/api/chat/send", "10.0.0.7").getStatus());

		// The broader /api/** tier is a separate bucket and still has capacity.
		assertEquals(200, call("/api/products", "10.0.0.7").getStatus());
	}

	@Test
	void leavesUnmatchedPathsAlone() throws Exception {
		for (int i = 0; i < 20; i++) {
			assertEquals(200, call("/login", "10.0.0.8").getStatus());
		}
	}

	@Test
	void doesNothingWhenDisabled() throws Exception {
		properties.setEnabled(false);

		for (int i = 0; i < 20; i++) {
			assertEquals(200, call("/api/products", "10.0.0.9").getStatus());
		}
	}

	/**
	 * X-Forwarded-For must be ignored unless trusted: otherwise a caller sets it
	 * themselves and gets a fresh bucket on every request.
	 */
	@Test
	void ignoresForwardedForByDefault() throws Exception {
		for (int i = 0; i < 5; i++) {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
			request.setRequestURI("/api/products");
			request.setRemoteAddr("10.0.0.10");
			request.addHeader("X-Forwarded-For", "1.2.3." + i);
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(request, response, new MockFilterChain());
			assertEquals(200, response.getStatus());
		}

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
		request.setRequestURI("/api/products");
		request.setRemoteAddr("10.0.0.10");
		request.addHeader("X-Forwarded-For", "9.9.9.9");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(429, response.getStatus(), "spoofed X-Forwarded-For must not reset the bucket");
	}

	@Test
	void honoursForwardedForWhenTrusted() throws Exception {
		properties.setTrustForwardedFor(true);

		for (int i = 0; i < 5; i++) {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
			request.setRequestURI("/api/products");
			request.setRemoteAddr("10.0.0.11");
			request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.11");
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(request, response, new MockFilterChain());
			assertEquals(200, response.getStatus());
		}

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
		request.setRequestURI("/api/products");
		request.setRemoteAddr("10.0.0.11");
		request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.11");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(429, response.getStatus());
	}
}
