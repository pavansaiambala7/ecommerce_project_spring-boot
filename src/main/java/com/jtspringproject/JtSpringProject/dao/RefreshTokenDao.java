package com.jtspringproject.JtSpringProject.dao;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.RefreshToken;

@Repository
public interface RefreshTokenDao extends JpaRepository<RefreshToken, Integer> {

	@EntityGraph(attributePaths = "user")
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Modifying
	@Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND t.revoked = false")
	int revokeAllForUser(@Param("userId") int userId);

	@Modifying
	@Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff OR t.revoked = true")
	int deleteExpiredAndRevoked(@Param("cutoff") Instant cutoff);
}
