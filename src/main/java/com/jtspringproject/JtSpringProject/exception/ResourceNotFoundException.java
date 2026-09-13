package com.jtspringproject.JtSpringProject.exception;

/**
 * Thrown when a requested entity does not exist. Translated to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

	public static ResourceNotFoundException of(String resource, Object id) {
		return new ResourceNotFoundException(resource + " not found with id: " + id);
	}
}
