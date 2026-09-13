package com.jtspringproject.JtSpringProject.support;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.jtspringproject.JtSpringProject.security.AppUserDetailsService;
import com.jtspringproject.JtSpringProject.security.JwtService;

/**
 * Collaborators that {@code @WebMvcTest} slices need but do not provide.
 *
 * <p>The slice picks up {@code JwtAuthenticationFilter} because it is a
 * {@code Filter} component, and then fails to construct it because
 * {@link JwtService} and {@link AppUserDetailsService} are ordinary services
 * outside the web layer. Supplying mocks here keeps every slice test from
 * repeating the same two {@code @MockBean} declarations.
 */
@TestConfiguration
public class SliceTestConfig {

	@Bean
	public JwtService jwtService() {
		return Mockito.mock(JwtService.class);
	}

	@Bean
	public AppUserDetailsService appUserDetailsService() {
		return Mockito.mock(AppUserDetailsService.class);
	}
}
