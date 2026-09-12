package com.jtspringproject.JtSpringProject.services;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.userDao;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.User;

@Service
public class userService {
	private final userDao userDao;
	private final PasswordEncoder passwordEncoder;

	public userService(userDao userDao, PasswordEncoder passwordEncoder) {
		this.userDao = userDao;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public List<User> getUsers() {
		return this.userDao.getAllUser();
	}

	@Transactional
	public User addUser(User user) {
		try {
			user.setPassword(passwordEncoder.encode(user.getPassword()));
			return this.userDao.saveUser(user);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessRuleException("Username '" + user.getUsername() + "' is already taken.");
		}
	}

	@Transactional
	public User register(String username, String email, String rawPassword, String address) {
		if (checkUserExists(username)) {
			throw new BusinessRuleException("Username '" + username + "' is already taken.");
		}
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setAddress(address);
		user.setRole(User.ROLE_NORMAL);
		user.setPassword(rawPassword);
		return addUser(user);
	}

	@Transactional(readOnly = true)
	public boolean checkUserExists(String username) {
		return this.userDao.userExists(username);
	}

	/**
	 * Looks a user up by username.
	 *
	 * <p>This previously re-hashed and re-saved plain-text passwords on read. That
	 * ran on the unauthenticated login path - before credentials were verified - so
	 * any request naming a known username triggered a bcrypt hash and a database
	 * write. Seeded passwords are hashed by migration V4 instead.
	 */
	@Transactional(readOnly = true)
	public User getUserByUsername(String username) {
		return userDao.getUserByUsername(username);
	}

	@Transactional(readOnly = true)
	public User getUserById(int id) {
		return this.userDao.getUserById(id);
	}

	@Transactional(readOnly = true)
	public User requireUserById(int id) {
		User user = this.userDao.getUserById(id);
		if (user == null) {
			throw ResourceNotFoundException.of("User", id);
		}
		return user;
	}

	@Transactional(readOnly = true)
	public User requireUserByUsername(String username) {
		User user = this.userDao.getUserByUsername(username);
		if (user == null) {
			throw new ResourceNotFoundException("User not found: " + username);
		}
		return user;
	}

	/**
	 * Updates a profile. Callers must resolve {@code userId} from the authenticated
	 * principal, never from request input - taking it from a hidden form field let
	 * any user rewrite any other account, including the administrator's.
	 */
	@Transactional
	public User updateUserProfile(int userId, String username, String email, String password, String address) {
		User existingUser = this.userDao.getUserById(userId);
		if (existingUser == null) {
			throw ResourceNotFoundException.of("User", userId);
		}

		if (username != null && !username.equals(existingUser.getUsername())) {
			if (this.userDao.userExists(username)) {
				throw new BusinessRuleException("Username '" + username + "' is already taken.");
			}
			existingUser.setUsername(username);
		}

		existingUser.setEmail(email);
		existingUser.setAddress(address);

		if (password != null && !password.isBlank()) {
			existingUser.setPassword(passwordEncoder.encode(password));
		}

		return this.userDao.saveUser(existingUser);
	}
}
