package com.jtspringproject.JtSpringProject.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Populates the security context from a {@code Authorization: Bearer <token>}
 * header. Invalid or absent tokens leave the context empty and let the
 * authorization rules decide; this filter never rejects a request itself.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private static final String HEADER = "Authorization";
	private static final String PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final AppUserDetailsService userDetailsService;

	public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		String token = extractToken(request);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			Claims claims = jwtService.parseToken(token);
			Integer userId = jwtService.extractUserId(claims);
			if (userId != null) {
				try {
					AppUserDetails principal = userDetailsService.loadUserById(userId);
					UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
							principal, null, principal.getAuthorities());
					authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authentication);
				} catch (UsernameNotFoundException e) {
					// Token references an account that no longer exists.
					log.debug("Rejected token for unknown user id {}", userId);
				}
			}
		}

		chain.doFilter(request, response);
	}

	private String extractToken(HttpServletRequest request) {
		String header = request.getHeader(HEADER);
		if (header != null && header.startsWith(PREFIX)) {
			return header.substring(PREFIX.length()).trim();
		}
		return null;
	}
}
