package com.jtspringproject.JtSpringProject.ratelimit;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jtspringproject.JtSpringProject.security.ApiErrorWriter;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Token-bucket rate limiting.
 *
 * <p>Registered ahead of the Spring Security filter chain so that abusive traffic
 * is rejected before any authentication work happens; that also lets it protect
 * the login endpoints themselves from credential stuffing. The trade-off is that
 * the caller is not yet authenticated here, so buckets are keyed by client
 * address rather than by principal.
 *
 * <p>Responses are written directly rather than by throwing: exceptions raised in
 * a servlet filter never reach {@code @RestControllerAdvice}, so an exception
 * here would surface as a container error page instead of the API error shape.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
	private static final String HEADER_LIMIT = "X-RateLimit-Limit";
	private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
	private static final String HEADER_RETRY_AFTER = "Retry-After";

	private final RateLimitService rateLimitService;
	private final RateLimitProperties properties;
	private final ApiErrorWriter errorWriter;

	public RateLimitFilter(RateLimitService rateLimitService, RateLimitProperties properties,
			ApiErrorWriter errorWriter) {
		this.rateLimitService = rateLimitService;
		this.properties = properties;
		this.errorWriter = errorWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		Optional<RateLimitProperties.Tier> match = rateLimitService.resolveTier(request.getRequestURI());
		if (match.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}

		RateLimitProperties.Tier tier = match.get();
		String clientKey = resolveClientKey(request);
		ConsumptionProbe probe = rateLimitService.tryConsume(clientKey, tier);

		response.setHeader(HEADER_LIMIT, String.valueOf(tier.getCapacity()));

		if (probe.isConsumed()) {
			response.setHeader(HEADER_REMAINING, String.valueOf(probe.getRemainingTokens()));
			chain.doFilter(request, response);
			return;
		}

		long retryAfterSeconds = Math.max(1,
				TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
		response.setHeader(HEADER_REMAINING, "0");
		response.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfterSeconds));

		log.warn("Rate limit exceeded: tier={} client={} path={}", tier.getName(), clientKey,
				request.getRequestURI());

		errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(),
				"Rate limit exceeded. Retry in " + retryAfterSeconds + " seconds.");
	}

	/**
	 * Identifies the caller.
	 *
	 * <p>X-Forwarded-For is only consulted when explicitly trusted. Behind a proxy
	 * that does not overwrite it, an attacker controls the header and can present a
	 * fresh identity on every request.
	 */
	private String resolveClientKey(HttpServletRequest request) {
		if (properties.isTrustForwardedFor()) {
			String forwarded = request.getHeader("X-Forwarded-For");
			if (forwarded != null && !forwarded.isBlank()) {
				return forwarded.split(",")[0].trim();
			}
		}
		String remote = request.getRemoteAddr();
		return remote == null ? "unknown" : remote;
	}
}
