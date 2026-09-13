package com.jtspringproject.JtSpringProject.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.jtspringproject.JtSpringProject.models.User;

/**
 * Authenticated principal carrying the database id, so controllers never have to
 * re-resolve the acting user from request input.
 */
public class AppUserDetails implements UserDetails {

	private static final long serialVersionUID = 1L;

	private final int id;
	private final String username;
	private final transient String password;
	private final List<GrantedAuthority> authorities;

	public AppUserDetails(int id, String username, String password, List<GrantedAuthority> authorities) {
		this.id = id;
		this.username = username;
		this.password = password;
		this.authorities = authorities;
	}

	public static AppUserDetails from(User user) {
		return new AppUserDetails(user.getId(), user.getUsername(), user.getPassword(), authoritiesFor(user));
	}

	/**
	 * Administrators also receive ROLE_USER. Previously an admin held only
	 * ROLE_ADMIN, and the storefront rules required ROLE_USER, so administrators
	 * were locked out of every non-admin page.
	 */
	public static List<GrantedAuthority> authoritiesFor(User user) {
		if (user.isAdmin()) {
			return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"));
		}
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	public int getId() {
		return id;
	}

	public boolean isAdmin() {
		return authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
