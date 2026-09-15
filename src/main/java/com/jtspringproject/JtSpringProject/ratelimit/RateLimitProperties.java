package com.jtspringproject.JtSpringProject.ratelimit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limit configuration.
 *
 * <p>Tiers are evaluated in order and the first matching tier wins, so more
 * specific paths must be listed before broader ones.
 */
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

	private boolean enabled = true;

	/**
	 * Whether to take the client address from X-Forwarded-For.
	 *
	 * <p>Off by default. When nothing strips or overwrites the header at the edge,
	 * a caller can set it freely and mint a new rate-limit identity per request,
	 * which makes the limiter worse than useless.
	 */
	private boolean trustForwardedFor = false;

	/** Maximum number of distinct client keys tracked before eviction. */
	private long maxTrackedClients = 100_000;

	private List<Tier> tiers = defaultTiers();

	private static List<Tier> defaultTiers() {
		List<Tier> defaults = new ArrayList<>();
		// Paid LLM calls: the tightest limits.
		defaults.add(new Tier("chat", List.of("/api/chat/**"), 10, Duration.ofMinutes(1)));
		defaults.add(new Tier("reindex", List.of("/api/search/reindex"), 2, Duration.ofHours(1)));
		defaults.add(new Tier("search", List.of("/api/search", "/api/search/**"), 30, Duration.ofMinutes(1)));
		// Credential endpoints: brute-force protection.
		defaults.add(new Tier("auth", List.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh",
				"/userloginvalidate", "/admin/loginvalidate", "/newuserregister"), 5, Duration.ofMinutes(1)));
		// Payment webhooks, deliberately generous. Razorpay delivers from shared
		// addresses, so every merchant's callbacks would share one bucket keyed
		// by IP; throttling them means answering non-2xx, which makes Razorpay
		// retry, which spends more of the same budget. Forged calls are stopped
		// by signature verification, not by this limit - its only job is to cap
		// the damage from a flood of unsigned requests.
		defaults.add(new Tier("webhook", List.of("/api/payments/razorpay/webhook"),
				600, Duration.ofMinutes(1)));
		// Everything else under the API.
		defaults.add(new Tier("api", List.of("/api/**"), 100, Duration.ofMinutes(1)));
		return defaults;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isTrustForwardedFor() {
		return trustForwardedFor;
	}

	public void setTrustForwardedFor(boolean trustForwardedFor) {
		this.trustForwardedFor = trustForwardedFor;
	}

	public long getMaxTrackedClients() {
		return maxTrackedClients;
	}

	public void setMaxTrackedClients(long maxTrackedClients) {
		this.maxTrackedClients = maxTrackedClients;
	}

	public List<Tier> getTiers() {
		return tiers;
	}

	public void setTiers(List<Tier> tiers) {
		this.tiers = tiers;
	}

	public static class Tier {

		private String name;
		private List<String> paths = new ArrayList<>();
		private long capacity;
		private Duration period = Duration.ofMinutes(1);

		public Tier() {
		}

		public Tier(String name, List<String> paths, long capacity, Duration period) {
			this.name = name;
			this.paths = paths;
			this.capacity = capacity;
			this.period = period;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<String> getPaths() {
			return paths;
		}

		public void setPaths(List<String> paths) {
			this.paths = paths;
		}

		public long getCapacity() {
			return capacity;
		}

		public void setCapacity(long capacity) {
			this.capacity = capacity;
		}

		public Duration getPeriod() {
			return period;
		}

		public void setPeriod(Duration period) {
			this.period = period;
		}
	}
}
