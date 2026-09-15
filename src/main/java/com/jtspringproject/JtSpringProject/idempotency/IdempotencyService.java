package com.jtspringproject.JtSpringProject.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;

/**
 * Makes an endpoint safe to call twice with the same Idempotency-Key.
 *
 * <p>Checkout and payment both take money and move stock, and both were
 * unguarded: a double-tap, an impatient second click, or a proxy retrying a
 * request whose response it never saw would each create a second order. The
 * customer is charged twice and nobody can tell the duplicate from a genuine
 * second purchase.
 *
 * <p>The guard is the table's primary key, not a read-then-write check. Two
 * simultaneous requests would both look for an existing row, both find none,
 * and both proceed - so the reservation is an INSERT, and losing that INSERT is
 * what tells the second caller to stand down.
 */
@Service
public class IdempotencyService {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

	/** Result of trying to claim a key. */
	public sealed interface Claim {
		/** This caller owns the key and should do the work. */
		record Proceed() implements Claim {
		}

		/** The work was already done; replay this instead of redoing it. */
		record Replay(int statusCode, String body) implements Claim {
		}

		/** An identical key is mid-flight in another request. */
		record InProgress() implements Claim {
		}
	}

	private final JdbcTemplate jdbc;
	private final ObjectMapper objectMapper;

	public IdempotencyService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
		this.jdbc = jdbc;
		this.objectMapper = objectMapper;
	}

	public Claim claim(String key, int customerId, String endpoint, String requestBody) {
		String hash = sha256(requestBody == null ? "" : requestBody);
		try {
			jdbc.update("""
					INSERT INTO idempotent_request (idempotency_key, customer_id, endpoint, request_hash)
					VALUES (?, ?, ?, ?)
					""", key, customerId, endpoint, hash);
			return new Claim.Proceed();
		} catch (DuplicateKeyException e) {
			return existing(key, customerId, endpoint, hash);
		}
	}

	private Claim existing(String key, int customerId, String endpoint, String hash) {
		return jdbc.query("""
				SELECT endpoint, request_hash, status_code, response_body, completed_at
				FROM idempotent_request
				WHERE customer_id = ? AND idempotency_key = ?
				""", rs -> {
			if (!rs.next()) {
				// The row vanished between the failed insert and this read -
				// only possible if cleanup ran at exactly that moment. Letting
				// the caller retry is safer than inventing a response.
				return new Claim.InProgress();
			}

			// Reusing one key for a different request is a client bug. Replaying
			// the first response would hide it and look like the second request
			// silently did nothing, so it is rejected loudly instead.
			if (!endpoint.equals(rs.getString("endpoint")) || !hash.equals(rs.getString("request_hash"))) {
				throw new BusinessRuleException(
						"This Idempotency-Key was already used for a different request.");
			}

			if (rs.getTimestamp("completed_at") == null) {
				return new Claim.InProgress();
			}
			return new Claim.Replay(rs.getInt("status_code"), rs.getString("response_body"));
		}, customerId, key);
	}

	/** Stores the outcome so a later repeat of this key replays it. */
	public void complete(String key, int customerId, int statusCode, String responseBody) {
		jdbc.update("""
				UPDATE idempotent_request
				SET status_code = ?, response_body = ?, completed_at = CURRENT_TIMESTAMP
				WHERE customer_id = ? AND idempotency_key = ?
				""", statusCode, responseBody, customerId, key);
	}

	/**
	 * Drops a failed claim so the caller can retry.
	 *
	 * <p>Without this, a request that failed on a transient error would leave a
	 * permanent reservation and every retry with that key would report
	 * "in progress" forever.
	 */
	public void release(String key, int customerId) {
		try {
			jdbc.update("DELETE FROM idempotent_request WHERE customer_id = ? AND idempotency_key = ? "
					+ "AND completed_at IS NULL", customerId, key);
		} catch (Exception e) {
			log.warn("Could not release idempotency key after failure", e);
		}
	}

	/**
	 * Runs the action once per key, replaying the stored response on a repeat.
	 *
	 * <p>Controllers call this rather than driving claim/complete/release
	 * themselves - forgetting the release on a failure path would leave a
	 * permanent reservation, and every later retry with that key would report
	 * "in progress" forever.
	 */
	public <T> ResponseEntity<T> run(String key, int customerId, String endpoint, String requestBody,
			Supplier<ResponseEntity<T>> action) {

		Claim claim = claim(key, customerId, endpoint, requestBody);

		if (claim instanceof Claim.InProgress) {
			// 409 rather than a wait: the first request is still working, and
			// blocking here would hold a connection for as long as it takes.
			throw new BusinessRuleException(
					"A request with this Idempotency-Key is still in progress. Retry shortly.");
		}

		if (claim instanceof Claim.Replay replay) {
			log.info("Replaying idempotent response for key {} (customer {})", key, customerId);
			return rebuild(replay);
		}

		try {
			ResponseEntity<T> response = action.get();
			complete(key, customerId, response.getStatusCode().value(), serialise(response.getBody()));
			return response;
		} catch (RuntimeException e) {
			// A failed attempt must not burn the key. The caller retried because
			// something went wrong; they need that retry to actually run.
			release(key, customerId);
			throw e;
		}
	}

	@SuppressWarnings("unchecked")
	private <T> ResponseEntity<T> rebuild(Claim.Replay replay) {
		try {
			return (ResponseEntity<T>) ResponseEntity.status(replay.statusCode())
					.header("Idempotent-Replay", "true")
					.body(objectMapper.readValue(replay.body(), Object.class));
		} catch (Exception e) {
			throw new IllegalStateException("Stored idempotent response could not be read back", e);
		}
	}

	private String serialise(Object body) {
		try {
			return objectMapper.writeValueAsString(body);
		} catch (Exception e) {
			throw new IllegalStateException("Could not store idempotent response", e);
		}
	}

	public Optional<String> validate(String key) {
		if (key == null || key.isBlank()) {
			return Optional.empty();
		}
		String trimmed = key.trim();
		if (trimmed.length() > 120) {
			throw new BusinessRuleException("Idempotency-Key must be at most 120 characters.");
		}
		return Optional.of(trimmed);
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
		}
	}
}
