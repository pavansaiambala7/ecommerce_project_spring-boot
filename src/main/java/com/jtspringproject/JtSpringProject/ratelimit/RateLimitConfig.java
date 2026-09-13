package com.jtspringproject.JtSpringProject.ratelimit;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.jtspringproject.JtSpringProject.security.ApiErrorWriter;

@Configuration
public class RateLimitConfig {

	/**
	 * Registers the rate limiter ahead of Spring Security.
	 *
	 * <p>{@code HIGHEST_PRECEDENCE + 10} places it before the security filter
	 * chain (which Boot registers at 0 by default) so that throttled requests cost
	 * no authentication work, and so login endpoints are themselves protected.
	 */
	@Bean
	public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
			RateLimitService rateLimitService,
			RateLimitProperties properties,
			ApiErrorWriter errorWriter) {

		FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
				new RateLimitFilter(rateLimitService, properties, errorWriter));
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		registration.addUrlPatterns("/*");
		registration.setName("rateLimitFilter");
		return registration;
	}
}
