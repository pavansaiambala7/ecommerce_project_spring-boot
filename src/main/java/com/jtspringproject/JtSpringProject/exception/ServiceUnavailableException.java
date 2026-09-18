package com.jtspringproject.JtSpringProject.exception;

/**
 * A third-party service this request depends on could not answer. Maps to 503:
 * the request was fine, and retrying later may succeed.
 */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String message) {
		super(message);
	}

	public ServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
