package com.jtspringproject.JtSpringProject.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;

import jakarta.validation.ConstraintViolationException;

/**
 * Translates exceptions raised by the API controllers into the standard
 * {@link ApiResponse} error shape with an appropriate status code.
 *
 * <p>Scoped to the API package so the server-rendered controllers keep their own
 * HTML error handling.
 *
 * <p>Note this cannot intercept exceptions thrown from servlet filters; the rate
 * limiter and the security entry points write their own responses through
 * {@code ApiErrorWriter} to produce an identical body.
 */
@RestControllerAdvice(basePackages = "com.jtspringproject.JtSpringProject.controller.api")
public class GlobalApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
	}

	/** Well-formed request that conflicts with domain state. */
	@ExceptionHandler(BusinessRuleException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		Map<String, String> errors = new HashMap<>();
		for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
			errors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", errors));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
		Map<String, String> errors = new HashMap<>();
		e.getConstraintViolations().forEach(violation ->
				errors.put(violation.getPropertyPath().toString(), violation.getMessage()));
		return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", errors));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		String required = e.getRequiredType() == null ? "the expected type" : e.getRequiredType().getSimpleName();
		return ResponseEntity.badRequest().body(ApiResponse.error(
				"Parameter '" + e.getName() + "' could not be converted to " + required + "."));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.error("Required parameter '" + e.getParameterName() + "' is missing."));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.error("Request body is missing or malformed."));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.error("You do not have permission to perform this action."));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
		// Deliberately generic: distinguishing "no such user" from "wrong password"
		// turns the endpoint into an account enumeration oracle.
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ApiResponse.error("Invalid credentials."));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
		log.warn("Data integrity violation", e);
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.error("The request conflicts with existing data."));
	}

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
				.body(ApiResponse.error(e.getMessage()));
	}

	/** An upstream dependency failed; the message tells the shopper what to do instead. */
	@ExceptionHandler(ServiceUnavailableException.class)
	public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(e.getMessage()));
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.error("No endpoint for " + e.getHttpMethod() + " " + e.getRequestURL() + "."));
	}

	/**
	 * Catch-all. Logs the full detail with a correlation id and returns only that
	 * id, so operators can find the stack trace without the client receiving
	 * internal details.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		String errorId = UUID.randomUUID().toString();
		log.error("Unhandled exception [errorId={}]", errorId, e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error("An unexpected error occurred. Reference: " + errorId));
	}
}
