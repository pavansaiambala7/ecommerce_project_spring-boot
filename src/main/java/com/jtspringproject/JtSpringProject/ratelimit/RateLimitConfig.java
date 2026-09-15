package com.jtspringproject.JtSpringProject.ratelimit;

import javax.sql.DataSource;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.jtspringproject.JtSpringProject.security.ApiErrorWriter;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;

@Configuration
public class RateLimitConfig {

	/**
	 * Where bucket state is kept.
	 *
	 * <p>PostgreSQL rather than the heap. An in-memory limiter resets every
	 * bucket on each deploy - and a deploy is exactly when the application can
	 * least absorb a flood - and cannot be shared by a second instance, which
	 * would silently multiply every configured limit by the instance count.
	 *
	 * <p>Exposed as a bean so tests can substitute an in-memory implementation
	 * and run without a database.
	 */
	@Bean
	public ProxyManager<Long> rateLimitProxyManager(DataSource dataSource) {
		return Bucket4jPostgreSQL.advisoryLockBasedBuilder(dataSource)
				.table("rate_limit_bucket")
				.idColumn("id")
				.stateColumn("state")
				.build();
	}

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
