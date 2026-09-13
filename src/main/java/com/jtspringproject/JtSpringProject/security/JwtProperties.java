package com.jtspringproject.JtSpringProject.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. The signing secret has no default on purpose: a fallback value
 * committed to source control is equivalent to no authentication at all, so the
 * application refuses to start without one.
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

	/** HMAC-SHA signing secret. Must be at least 32 bytes for HS256. */
	private String secret;

	/** Lifetime of an access token. */
	private long accessTokenMinutes = 15;

	/** Lifetime of a refresh token. */
	private long refreshTokenDays = 7;

	private String issuer = "jtspringproject";

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public long getAccessTokenMinutes() {
		return accessTokenMinutes;
	}

	public void setAccessTokenMinutes(long accessTokenMinutes) {
		this.accessTokenMinutes = accessTokenMinutes;
	}

	public long getRefreshTokenDays() {
		return refreshTokenDays;
	}

	public void setRefreshTokenDays(long refreshTokenDays) {
		this.refreshTokenDays = refreshTokenDays;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}
}
