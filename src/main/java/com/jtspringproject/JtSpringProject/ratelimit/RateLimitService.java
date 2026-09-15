package com.jtspringproject.JtSpringProject.ratelimit;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;

/**
 * Resolves a token bucket per (client, tier) pair, backed by PostgreSQL.
 *
 * <p>Bucket state used to live in a Caffeine cache on the heap, which made the
 * limiter weakest at exactly the wrong moments:
 *
 * <ul>
 * <li><b>Every deploy reset every bucket.</b> A client being throttled only had
 * to wait for the next release - and a deploy is precisely when the application
 * is least able to absorb a flood.</li>
 * <li><b>It could never scale past one instance.</b> Two instances would each
 * keep their own counts, quietly doubling every configured limit.</li>
 * </ul>
 *
 * <p>The PostgreSQL backend keeps each bucket in a row and serialises updates
 * with an advisory lock, so concurrent requests are counted correctly whether
 * they land on one instance or several. It costs one short database round trip
 * per limited request, which is a fair price for a limit that is actually
 * enforced.
 */
@Service
public class RateLimitService {

	private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

	private final RateLimitProperties properties;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();
	private final ProxyManager<Long> proxyManager;

	public RateLimitService(RateLimitProperties properties, ProxyManager<Long> proxyManager) {
		this.properties = properties;
		this.proxyManager = proxyManager;
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
	 *
	 * <p>If the database is unreachable the request is allowed through. A
	 * limiter that fails closed would turn a database blip into a total outage,
	 * which is a worse failure than briefly not throttling.
	 */
	public ConsumptionProbe tryConsume(String clientKey, RateLimitProperties.Tier tier) {
		try {
			BucketProxy bucket = proxyManager.builder()
					.build(bucketId(tier.getName(), clientKey), () -> configurationFor(tier));
			return bucket.tryConsumeAndReturnRemaining(1);
		} catch (Exception e) {
			log.warn("Rate limit store unavailable, allowing request through: {}", e.getMessage());
			return ConsumptionProbe.consumed(tier.getCapacity() - 1, 0);
		}
	}

	/**
	 * Maps "tier|client" onto the bigint primary key the backend requires.
	 *
	 * <p>CRC32 of the composite key. A collision would let two clients share a
	 * bucket, so the tier name is included to keep separate limits apart even in
	 * that case, and 32 bits over the number of addresses one instance sees
	 * makes it vanishingly unlikely to matter.
	 */
	private static long bucketId(String tierName, String clientKey) {
		CRC32 crc = new CRC32();
		crc.update((tierName + "|" + clientKey).getBytes(StandardCharsets.UTF_8));
		return crc.getValue();
	}

	private BucketConfiguration configurationFor(RateLimitProperties.Tier tier) {
		Bandwidth limit = Bandwidth.builder()
				.capacity(tier.getCapacity())
				.refillGreedy(tier.getCapacity(), tier.getPeriod())
				.build();
		return BucketConfiguration.builder().addLimit(limit).build();
	}

	/** Visible for tests: drops one client's bucket in a tier. */
	public void reset(String clientKey, RateLimitProperties.Tier tier) {
		try {
			proxyManager.removeProxy(bucketId(tier.getName(), clientKey));
		} catch (Exception e) {
			log.debug("Could not remove bucket for {}", clientKey, e);
		}
	}
}
