package com.jtspringproject.JtSpringProject.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.jtspringproject.JtSpringProject.models.User;
import com.jtspringproject.JtSpringProject.services.userService;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	private final userService userService;

	public SecurityConfiguration(userService userService) {
		this.userService = userService;
	}

	@Bean
	@Order(1)
	SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/admin/**")
			.authorizeHttpRequests(requests -> requests
				.requestMatchers("/admin/login").permitAll()
				.requestMatchers("/admin/**").hasRole("ADMIN"))
			.formLogin(login -> login
				.loginPage("/admin/login")
				.loginProcessingUrl("/admin/loginvalidate")
				.successHandler((request, response, authentication) -> {
					response.sendRedirect("/admin/");
				})
				.failureHandler((request, response, exception) -> {
					response.sendRedirect("/admin/login?error=true");
				}))
			.logout(logout -> logout
				.logoutRequestMatcher(new AntPathRequestMatcher("/admin/logout", "GET"))
				.logoutSuccessUrl("/admin/login")
				.deleteCookies("JSESSIONID"))
			.exceptionHandling(exception -> exception
				.accessDeniedPage("/403"));
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(requests -> requests
				.requestMatchers("/login", "/register", "/newuserregister").permitAll()
				.requestMatchers("/api/**").permitAll()
				.anyRequest().hasRole("USER"))
			.formLogin(login -> login
				.loginPage("/login")
				.loginProcessingUrl("/userloginvalidate")
				.successHandler((request, response, authentication) -> {
					response.sendRedirect("/");
				})
				.failureHandler((request, response, exception) -> {
					response.sendRedirect("/login?error=true");
				}))
			.logout(logout -> logout
				.logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
				.logoutSuccessUrl("/login")
				.deleteCookies("JSESSIONID"))
			.exceptionHandling(exception -> exception
				.accessDeniedPage("/403"))
			.csrf(csrf -> csrf
				.ignoringRequestMatchers("/api/**"));
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService() {
		return username -> {
			User user = userService.getUserByUsername(username);
			if (user == null) {
				throw new UsernameNotFoundException("User with username " + username + " not found.");
			}
			String role = "ROLE_ADMIN".equals(user.getRole()) ? "ADMIN" : "USER";

			return org.springframework.security.core.userdetails.User
					.withUsername(username)
					.password(user.getPassword())
					.roles(role)
					.build();
		};
	}
}
