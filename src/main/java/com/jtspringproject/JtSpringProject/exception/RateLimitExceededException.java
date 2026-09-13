package com.jtspringproject.JtSpringProject.exception;

/**
 * Thrown when a caller exceeds its configured request quota. Translated to HTTP 429.
 *
 * <p>Note: the rate limit filter runs ahead of Spring MVC and therefore writes its
 * own response rather than relying on {@code @RestControllerAdvice}, which cannot
 * intercept exceptions thrown from servlet filters. This type exists for the cases
 * where a limit is enforced from inside the MVC layer.
 */
public class RateLimitExceededException extends RuntimeException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(String message, long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
