package com.jtspringproject.JtSpringProject.ratelimit;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Resolves a token bucket per (client, tier) pair.
 *
 * <p>Buckets live in a bounded, expiring cache. A plain map keyed by client
 * address is the usual way this component turns into a memory leak, since every
 * new address allocates an entry that is never reclaimed.
 */
@Service
public class RateLimitService {

	private final RateLimitProperties properties;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();
	private final Cache<String, Bucket> buckets;

	public RateLimitService(RateLimitProperties properties) {
		this.properties = properties;
		this.buckets = Caffeine.newBuilder()
				.maximumSize(properties.getMaxTrackedClients())
				.expireAfterAccess(Duration.ofHours(2))
				.build();
	}

	/** Returns the first tier whose patterns match the path, if any. */
	public Optional<RateLimitProperties.Tier> resolveTier(String path) {
		if (!properties.isEnabled()) {
			return Optional.empty();
		}
		return properties.getTiers().stream()
				.filter(tier -> tier.getPaths().stream().anyMatch(p -> pathMatcher.match(p, path)))
				.findFirst();
	}

	/**
	 * Attempts to consume one token for this client under this tier.
	 */
	public ConsumptionProbe tryConsume(String clientKey, RateLimitProperties.Tier tier) {
		Bucket bucket = buckets.get(tier.getName() + "|" + clientKey, key -> newBucket(tier));
		return bucket.tryConsumeAndReturnRemaining(1);
	}

	private Bucket newBucket(RateLimitProperties.Tier tier) {
		Bandwidth limit = Bandwidth.builder()
				.capacity(tier.getCapacity())
				.refillGreedy(tier.getCapacity(), tier.getPeriod())
				.build();
		return Bucket.builder().addLimit(limit).build();
	}

	/** Visible for tests. */
	public void reset() {
		buckets.invalidateAll();
	}
}
