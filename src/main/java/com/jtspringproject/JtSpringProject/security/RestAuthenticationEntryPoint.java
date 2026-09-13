package com.jtspringproject.JtSpringProject.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Returns 401 JSON for unauthenticated API calls instead of redirecting to the
 * HTML login page, which is useless to an API client.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                "Authentication required. Supply a valid Bearer token.");
    }
}
