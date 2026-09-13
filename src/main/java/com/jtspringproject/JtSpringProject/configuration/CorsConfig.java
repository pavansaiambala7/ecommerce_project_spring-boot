package com.jtspringproject.JtSpringProject.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

	private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

	private final CorsProperties properties;

	public CorsConfig(CorsProperties properties) {
		this.properties = properties;
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();

		List<String> origins = properties.getAllowedOrigins();
		boolean hasWildcard = origins.stream().anyMatch(o -> o.contains("*"));

		if (hasWildcard) {
			// allowedOriginPatterns is the only form that can be combined with
			// credentials; plain allowedOrigins("*") is rejected by the browser.
			config.setAllowedOriginPatterns(origins);
			if (properties.isAllowCredentials()) {
				log.warn("CORS is configured with wildcard origins {} AND credentials enabled. "
						+ "Prefer an explicit origin list for anything reachable from the internet.", origins);
			}
		} else {
			config.setAllowedOrigins(origins);
		}

		config.setAllowedMethods(properties.getAllowedMethods());
		config.setAllowedHeaders(properties.getAllowedHeaders());
		config.setExposedHeaders(properties.getExposedHeaders());
		config.setAllowCredentials(properties.isAllowCredentials());
		config.setMaxAge(properties.getMaxAgeSeconds());

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
