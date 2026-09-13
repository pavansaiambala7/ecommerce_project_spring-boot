package com.jtspringproject.JtSpringProject.support;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import com.jtspringproject.JtSpringProject.security.AppUserDetails;

public class WithMockAppUserSecurityContextFactory implements WithSecurityContextFactory<WithMockAppUser> {

	@Override
	public SecurityContext createSecurityContext(WithMockAppUser annotation) {
		List<GrantedAuthority> authorities = annotation.admin()
				? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
				: List.of(new SimpleGrantedAuthority("ROLE_USER"));

		AppUserDetails principal = new AppUserDetails(
				annotation.id(), annotation.username(), "n/a", authorities);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
		return context;
	}
}
