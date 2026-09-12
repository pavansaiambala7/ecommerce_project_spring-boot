package com.jtspringproject.JtSpringProject.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.jtspringproject.JtSpringProject.dao.userDao;
import com.jtspringproject.JtSpringProject.models.User;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final userDao userDao;

	public AppUserDetailsService(userDao userDao) {
		this.userDao = userDao;
	}

	@Override
	public AppUserDetails loadUserByUsername(String username) {
		User user = userDao.getUserByUsername(username);
		if (user == null) {
			throw new UsernameNotFoundException("User with username " + username + " not found.");
		}
		return AppUserDetails.from(user);
	}

	public AppUserDetails loadUserById(int id) {
		User user = userDao.getUserById(id);
		if (user == null) {
			throw new UsernameNotFoundException("User with id " + id + " not found.");
		}
		return AppUserDetails.from(user);
	}
}
