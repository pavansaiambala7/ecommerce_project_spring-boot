package com.jtspringproject.JtSpringProject.configuration;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS settings.
 *
 * <p>Origins are an explicit allow-list. A wildcard origin is deliberately not
 * the default: the CORS specification forbids combining {@code *} with
 * credentialed requests, and browsers reject the combination at runtime rather
 * than at startup, which makes it a slow failure to diagnose.
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

	private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:8080");

	private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

	private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "Accept", "X-Requested-With");

	/** Headers a browser client is allowed to read from the response. */
	private List<String> exposedHeaders = List.of("X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After");

	private boolean allowCredentials = true;

	/** Preflight cache lifetime, in seconds. */
	private long maxAgeSeconds = 3600;

	public List<String> getAllowedOrigins() {
		return allowedOrigins;
	}

	public void setAllowedOrigins(List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	public List<String> getAllowedMethods() {
		return allowedMethods;
	}

	public void setAllowedMethods(List<String> allowedMethods) {
		this.allowedMethods = allowedMethods;
	}

	public List<String> getAllowedHeaders() {
		return allowedHeaders;
	}

	public void setAllowedHeaders(List<String> allowedHeaders) {
		this.allowedHeaders = allowedHeaders;
	}

	public List<String> getExposedHeaders() {
		return exposedHeaders;
	}

	public void setExposedHeaders(List<String> exposedHeaders) {
		this.exposedHeaders = exposedHeaders;
	}

	public boolean isAllowCredentials() {
		return allowCredentials;
	}

	public void setAllowCredentials(boolean allowCredentials) {
		this.allowCredentials = allowCredentials;
	}

	public long getMaxAgeSeconds() {
		return maxAgeSeconds;
	}

	public void setMaxAgeSeconds(long maxAgeSeconds) {
		this.maxAgeSeconds = maxAgeSeconds;
	}
}
