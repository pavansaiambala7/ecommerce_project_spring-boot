package com.jtspringproject.JtSpringProject.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jtspringproject.JtSpringProject.models.User;

import io.jsonwebtoken.Claims;

class JwtServiceTest {

	private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long-padding";

	private JwtService jwtService;
	private User user;

	@BeforeEach
	void setUp() {
		JwtProperties properties = new JwtProperties();
		properties.setSecret(SECRET);
		jwtService = new JwtService(properties);
		jwtService.initialiseKey();

		user = new User();
		user.setId(42);
		user.setUsername("alice");
		user.setRole(User.ROLE_NORMAL);
	}

	@Test
	void issuesATokenCarryingTheUserId() {
		String token = jwtService.generateAccessToken(user);

		Claims claims = jwtService.parseToken(token);

		assertNotNull(claims);
		assertEquals(42, jwtService.extractUserId(claims));
		assertEquals("alice", jwtService.extractUsername(claims));
	}

	/**
	 * The subject is the immutable id rather than the username, so renaming an
	 * account neither invalidates a live token nor lets it resolve to a different
	 * account that later takes the old name.
	 */
	@Test
	void subjectIsTheIdNotTheUsername() {
		Claims claims = jwtService.parseToken(jwtService.generateAccessToken(user));

		assertEquals("42", claims.getSubject());
	}

	@Test
	void rejectsATamperedToken() {
		String token = jwtService.generateAccessToken(user);
		String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "bogussignature";

		assertNull(jwtService.parseToken(tampered));
	}

	@Test
	void rejectsATokenSignedWithAnotherKey() {
		JwtProperties other = new JwtProperties();
		other.setSecret("a-completely-different-secret-key-also-32-bytes");
		JwtService otherService = new JwtService(other);
		otherService.initialiseKey();

		String foreignToken = otherService.generateAccessToken(user);

		assertNull(jwtService.parseToken(foreignToken));
	}

	@Test
	void rejectsGarbage() {
		assertNull(jwtService.parseToken("not-a-token"));
		assertNull(jwtService.parseToken(""));
		assertNull(jwtService.parseToken(null));
	}

	/**
	 * A committed default signing key is equivalent to no authentication, so the
	 * service refuses to initialise without one.
	 */
	@Test
	void refusesToStartWithoutASecret() {
		JwtProperties blank = new JwtProperties();
		JwtService service = new JwtService(blank);

		IllegalStateException e = assertThrows(IllegalStateException.class, service::initialiseKey);
		assertTrue(e.getMessage().contains("app.jwt.secret"));
	}

	@Test
	void refusesToStartWithATooShortSecret() {
		JwtProperties shortSecret = new JwtProperties();
		shortSecret.setSecret("tooshort");
		JwtService service = new JwtService(shortSecret);

		assertThrows(IllegalStateException.class, service::initialiseKey);
	}

	@Test
	void refreshTokensAreRandom() {
		assertNotEquals(jwtService.generateRefreshTokenValue(), jwtService.generateRefreshTokenValue());
	}
}
