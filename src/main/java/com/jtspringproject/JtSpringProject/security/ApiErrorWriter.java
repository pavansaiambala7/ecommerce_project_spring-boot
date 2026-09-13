package com.jtspringproject.JtSpringProject.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes an {@link ApiResponse} error body directly to the servlet response.
 *
 * <p>Needed because security entry points, access-denied handlers and the rate
 * limit filter all run outside Spring MVC, where {@code @RestControllerAdvice}
 * cannot reach them. Sharing this writer keeps their output identical to the
 * errors the advice produces.
 */
@Component
public class ApiErrorWriter {

	private final ObjectMapper objectMapper;

	public ApiErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletResponse response, int status, String message) throws IOException {
		if (response.isCommitted()) {
			return;
		}
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(message));
	}
}
