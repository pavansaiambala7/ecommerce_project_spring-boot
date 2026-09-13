package com.jtspringproject.JtSpringProject.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.RefreshTokenDao;
import com.jtspringproject.JtSpringProject.dto.response.TokenResponse;
import com.jtspringproject.JtSpringProject.models.RefreshToken;
import com.jtspringproject.JtSpringProject.models.User;
import com.jtspringproject.JtSpringProject.services.userService;

@Service
public class AuthService {

	private final userService userService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenDao refreshTokenDao;

	public AuthService(userService userService, PasswordEncoder passwordEncoder, JwtService jwtService,
			RefreshTokenDao refreshTokenDao) {
		this.userService = userService;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenDao = refreshTokenDao;
	}

	@Transactional
	public TokenResponse login(String username, String rawPassword) {
		User user = userService.getUserByUsername(username);

		// Compare against a dummy hash when the user is absent so that a missing
		// account and a wrong password take the same time to reject, and report
		// the same message either way.
		if (user == null) {
			passwordEncoder.matches(rawPassword, "$2b$10$ivfQvJ7Dn5G3P.ODbYYUuOLIqTmYoT05VOqZcqZ1VJb1Q0SPX9V4W");
			throw new BadCredentialsException("Invalid username or password.");
		}
		if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
			throw new BadCredentialsException("Invalid username or password.");
		}

		return issueTokens(user);
	}

	@Transactional
	public TokenResponse register(String username, String email, String rawPassword, String address) {
		User user = userService.register(username, email, rawPassword, address);
		return issueTokens(user);
	}

	/**
	 * Exchanges a refresh token for a new pair, rotating the old one. Rotation
	 * means a stolen refresh token is usable at most once before the legitimate
	 * client's next refresh invalidates it.
	 */
	@Transactional
	public TokenResponse refresh(String rawRefreshToken) {
		String hash = hash(rawRefreshToken);
		RefreshToken stored = refreshTokenDao.findByTokenHash(hash)
				.orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));

		if (!stored.isUsable()) {
			throw new BadCredentialsException("Refresh token is expired or revoked.");
		}

		stored.setRevoked(true);
		refreshTokenDao.save(stored);

		return issueTokens(stored.getUser());
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		refreshTokenDao.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
			token.setRevoked(true);
			refreshTokenDao.save(token);
		});
	}

	@Transactional
	public void revokeAllForUser(int userId) {
		refreshTokenDao.revokeAllForUser(userId);
	}

	private TokenResponse issueTokens(User user) {
		String accessToken = jwtService.generateAccessToken(user);
		String refreshValue = jwtService.generateRefreshTokenValue();

		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setTokenHash(hash(refreshValue));
		refreshToken.setUser(user);
		refreshToken.setExpiresAt(Instant.now().plus(jwtService.getRefreshTokenDays(), ChronoUnit.DAYS));
		refreshTokenDao.save(refreshToken);

		return new TokenResponse(accessToken, refreshValue, jwtService.getAccessTokenSeconds(),
				user.getUsername(), user.getRole());
	}

	private String hash(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required but unavailable", e);
		}
	}
}
