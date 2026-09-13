package com.jtspringproject.JtSpringProject.exception;

/**
 * Thrown when a request is well-formed but conflicts with the current state of
 * the domain (insufficient stock, illegal status transition, ...).
 * Translated to HTTP 409.
 */
public class BusinessRuleException extends RuntimeException {

	public BusinessRuleException(String message) {
		super(message);
	}
}
