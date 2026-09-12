package com.jtspringproject.JtSpringProject.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.jtspringproject.JtSpringProject.models.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

/**
 * Issues and verifies JWT access tokens.
 *
 * <p>The token subject is the immutable user id rather than the username, so
 * renaming an account does not silently invalidate or, worse, misattribute a
 * live token.
 */
@Service
public class JwtService {

	private static final int MIN_SECRET_BYTES = 32;
	private static final String CLAIM_USERNAME = "username";
	private static final String CLAIM_ROLE = "role";

	private final JwtProperties properties;
	private final SecureRandom secureRandom = new SecureRandom();
	private SecretKey signingKey;

	public JwtService(JwtProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void initialiseKey() {
		String secret = properties.getSecret();
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException(
					"app.jwt.secret is not configured. Set the JWT_SECRET environment variable to a random "
							+ "value of at least " + MIN_SECRET_BYTES + " bytes. Refusing to start with an "
							+ "unauthenticated API.");
		}
		byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES
					+ " bytes for HS256; got " + keyBytes.length + ".");
		}
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
	}

	public String generateAccessToken(User user) {
		Instant now = Instant.now();
		Instant expiry = now.plusSeconds(properties.getAccessTokenMinutes() * 60);

		return Jwts.builder()
				.subject(String.valueOf(user.getId()))
				.claim(CLAIM_USERNAME, user.getUsername())
				.claim(CLAIM_ROLE, user.getRole())
				.issuer(properties.getIssuer())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiry))
				.signWith(signingKey)
				.compact();
	}

	/**
	 * Generates an opaque refresh token. Refresh tokens are random rather than
	 * signed so that they can be revoked by deleting the stored hash.
	 */
	public String generateRefreshTokenValue() {
		byte[] bytes = new byte[48];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * Parses and verifies a token.
	 *
	 * @return the token claims, or {@code null} if the token is absent, malformed,
	 *         expired or not signed by this service.
	 */
	public Claims parseToken(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			return Jwts.parser()
					.verifyWith(signingKey)
					.requireIssuer(properties.getIssuer())
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (JwtException | IllegalArgumentException e) {
			return null;
		}
	}

	public Integer extractUserId(Claims claims) {
		if (claims == null || claims.getSubject() == null) {
			return null;
		}
		try {
			return Integer.valueOf(claims.getSubject());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public String extractUsername(Claims claims) {
		return claims == null ? null : claims.get(CLAIM_USERNAME, String.class);
	}

	public long getAccessTokenSeconds() {
		return properties.getAccessTokenMinutes() * 60;
	}

	public long getRefreshTokenDays() {
		return properties.getRefreshTokenDays();
	}
}
