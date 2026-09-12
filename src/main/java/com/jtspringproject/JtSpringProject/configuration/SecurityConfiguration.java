package com.jtspringproject.JtSpringProject.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import com.jtspringproject.JtSpringProject.security.AppUserDetailsService;
import com.jtspringproject.JtSpringProject.security.JwtAuthenticationFilter;
import com.jtspringproject.JtSpringProject.security.RestAccessDeniedHandler;
import com.jtspringproject.JtSpringProject.security.RestAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

	private final AppUserDetailsService userDetailsService;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
	private final RestAccessDeniedHandler restAccessDeniedHandler;
	private final CorsConfigurationSource corsConfigurationSource;

	public SecurityConfiguration(AppUserDetailsService userDetailsService,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationEntryPoint restAuthenticationEntryPoint,
			RestAccessDeniedHandler restAccessDeniedHandler,
			CorsConfigurationSource corsConfigurationSource) {
		this.userDetailsService = userDetailsService;
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
		this.restAccessDeniedHandler = restAccessDeniedHandler;
		this.corsConfigurationSource = corsConfigurationSource;
	}

	/**
	 * Stateless JWT chain for the REST API.
	 *
	 * <p>Every {@code /api/**} path used to be {@code permitAll()} with CSRF
	 * disabled, which published the full user table (password hashes included),
	 * allowed anonymous password changes on any account, and left product, order
	 * and refund operations open to the internet.
	 *
	 * <p>CSRF stays disabled here, but only because the chain is stateless and
	 * authenticates from an Authorization header. With no ambient cookie
	 * credential there is nothing for a cross-site request to ride on.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/api/**")
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				// Public: authentication and read-only catalogue access.
				.requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/paged", "/api/products/*")
					.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/search").permitAll()
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

				// Administrative: catalogue mutation, user directory, reindexing, refunds.
				.requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
				.requestMatchers(HttpMethod.PUT, "/api/products/*").hasRole("ADMIN")
				.requestMatchers(HttpMethod.DELETE, "/api/products/*").hasRole("ADMIN")
				.requestMatchers("/api/search/reindex").hasRole("ADMIN")
				.requestMatchers("/api/payments/refund/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*").hasRole("ADMIN")

				// Everything else requires an authenticated user; services
				// additionally enforce per-record ownership.
				.anyRequest().authenticated())
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(restAuthenticationEntryPoint)
				.accessDeniedHandler(restAccessDeniedHandler))
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/** Session + form login chain for the server-rendered admin pages. */
	@Bean
	@Order(2)
	SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/admin/**")
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers("/admin/login").permitAll()
				.requestMatchers("/admin/**").hasRole("ADMIN"))
			.formLogin(login -> login
				.loginPage("/admin/login")
				.loginProcessingUrl("/admin/loginvalidate")
				.successHandler((request, response, authentication) -> response.sendRedirect("/admin/"))
				.failureHandler((request, response, exception) -> response.sendRedirect("/admin/login?error=true")))
			.logout(logout -> logout
				.logoutRequestMatcher(new AntPathRequestMatcher("/admin/logout", "POST"))
				.logoutSuccessUrl("/admin/login")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"))
			.exceptionHandling(exception -> exception
				.accessDeniedPage("/403"));
		return http.build();
	}

	/** Session + form login chain for the storefront. */
	@Bean
	@Order(3)
	SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers("/login", "/register", "/newuserregister", "/403").permitAll()
				.requestMatchers("/css/**", "/js/**", "/images/**", "/static/**", "/favicon.ico").permitAll()
				.anyRequest().hasRole("USER"))
			.formLogin(login -> login
				.loginPage("/login")
				.loginProcessingUrl("/userloginvalidate")
				.successHandler((request, response, authentication) -> response.sendRedirect("/"))
				.failureHandler((request, response, exception) -> response.sendRedirect("/login?error=true")))
			.logout(logout -> logout
				// POST-only: a GET logout can be triggered by any third-party page
				// embedding the URL, letting an attacker sign the user out at will.
				.logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
				.logoutSuccessUrl("/login")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"))
			.exceptionHandling(exception -> exception
				.accessDeniedPage("/403"));
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService() {
		return userDetailsService;
	}

	@Bean
	DaoAuthenticationProvider daoAuthenticationProvider(PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		// Without this, a request for a non-existent username skips password
		// comparison and returns measurably faster, letting an attacker enumerate
		// valid accounts by timing.
		provider.setHideUserNotFoundExceptions(true);
		return provider;
	}

	@Bean
	AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
		return provider::authenticate;
	}
}
