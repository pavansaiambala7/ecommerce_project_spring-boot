package com.jtspringproject.JtSpringProject.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Populates the security context with a real {@link
 * com.jtspringproject.JtSpringProject.security.AppUserDetails} principal.
 *
 * <p>{@code @WithMockUser} supplies Spring's own {@code User} type, which the
 * controllers cannot use: they declare {@code @AuthenticationPrincipal
 * AppUserDetails} so that the acting user id comes from the token rather than
 * from request input.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@WithSecurityContext(factory = WithMockAppUserSecurityContextFactory.class)
public @interface WithMockAppUser {

	int id() default 1;

	String username() default "testuser";

	boolean admin() default false;
}
