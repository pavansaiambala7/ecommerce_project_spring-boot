package com.jtspringproject.JtSpringProject.geo;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.exception.ServiceUnavailableException;

/**
 * Fills in an address from the shopper's location or PIN code.
 *
 * <p>Both lookups go through the server rather than from the browser:
 * OpenStreetMap's Nominatim requires an identifying User-Agent, which browsers
 * do not let a page set; the responses are cached so a hundred shoppers in one
 * PIN code cost one upstream call; and the shopper's coordinates are only ever
 * sent onward by a server the shop operates.
 *
 * <p>Neither lookup is required. Every failure here surfaces as "type the
 * address instead", never as a checkout that cannot proceed.
 */
@Service
public class GeoService {

	private static final Logger log = LoggerFactory.getLogger(GeoService.class);

	/** An address as far as a lookup could determine it. Any field may be null. */
	public record GeoAddress(String line2, String city, String state, String pincode, String displayName) {
	}

	/** What a PIN code tells us: its district, state and the post offices in it. */
	public record PincodeInfo(String pincode, String city, String state, List<String> areas) {
	}

	private final HttpClient http;
	private final ObjectMapper objectMapper;
	private final String reverseUrl;
	private final String pincodeUrl;
	private final String userAgent;

	private final Cache<String, GeoAddress> reverseCache = Caffeine.newBuilder()
			.maximumSize(10_000).expireAfterWrite(Duration.ofHours(24)).build();
	private final Cache<String, PincodeInfo> pincodeCache = Caffeine.newBuilder()
			.maximumSize(20_000).expireAfterWrite(Duration.ofDays(7)).build();

	/** Nominatim's usage policy allows at most one request per second. */
	private final Object nominatimLock = new Object();
	private long lastNominatimCallNanos;

	public GeoService(ObjectMapper objectMapper,
			@Value("${app.geo.reverse-url:https://nominatim.openstreetmap.org/reverse}") String reverseUrl,
			@Value("${app.geo.pincode-url:https://api.postalpincode.in/pincode/}") String pincodeUrl,
			@Value("${app.geo.user-agent:ShopKart/1.0 (+https://github.com/pavansaiambala7/ecommerce_project_spring-boot)}") String userAgent) {
		this.objectMapper = objectMapper;
		this.reverseUrl = reverseUrl;
		this.pincodeUrl = pincodeUrl;
		this.userAgent = userAgent;
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(4))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	public GeoAddress reverse(double latitude, double longitude) {
		if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("Coordinates are out of range.");
		}
		// About eleven metres of precision: enough to find the street, coarse
		// enough that neighbours share a cache entry.
		String key = round(latitude) + "," + round(longitude);
		return reverseCache.get(key, k -> fetchReverse(latitude, longitude));
	}

	public PincodeInfo pincode(String pincode) {
		if (pincode == null || !pincode.matches("^[1-9][0-9]{5}$")) {
			throw new IllegalArgumentException("Enter a 6-digit PIN code.");
		}
		PincodeInfo cached = pincodeCache.getIfPresent(pincode);
		if (cached != null) {
			return cached;
		}
		PincodeInfo info = fetchPincode(pincode)
				.orElseThrow(() -> new ResourceNotFoundException("No post office found for PIN code " + pincode + "."));
		pincodeCache.put(pincode, info);
		return info;
	}

	// ------------------------------------------------------------------ upstream

	private GeoAddress fetchReverse(double latitude, double longitude) {
		String url = reverseUrl + "?format=jsonv2&addressdetails=1&zoom=18&accept-language=en"
				+ "&lat=" + enc(String.valueOf(latitude)) + "&lon=" + enc(String.valueOf(longitude));
		throttleNominatim();
		return parseReverse(get(url, "location lookup"));
	}

	private Optional<PincodeInfo> fetchPincode(String pincode) {
		return parsePincode(pincode, get(pincodeUrl + pincode, "PIN code lookup"));
	}

	private JsonNode get(String url, String what) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(6))
				.header("User-Agent", userAgent)
				.header("Accept", "application/json")
				.GET()
				.build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				log.warn("{} returned HTTP {}", what, response.statusCode());
				throw new ServiceUnavailableException("The " + what + " service is not responding. "
						+ "Please enter the address manually.");
			}
			return objectMapper.readTree(response.body());
		} catch (IOException e) {
			log.warn("{} failed: {}", what, e.getMessage());
			throw new ServiceUnavailableException("The " + what + " service could not be reached. "
					+ "Please enter the address manually.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ServiceUnavailableException("The " + what + " was interrupted.", e);
		}
	}

	private void throttleNominatim() {
		synchronized (nominatimLock) {
			long waitNanos = lastNominatimCallNanos + 1_000_000_000L - System.nanoTime();
			if (waitNanos > 0) {
				try {
					// Thread.sleep(Duration) is Java 19+; this project targets 17.
					java.util.concurrent.TimeUnit.NANOSECONDS.sleep(waitNanos);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			lastNominatimCallNanos = System.nanoTime();
		}
	}

	// ------------------------------------------------------------------ parsing

	/** Package-private so the mapping can be tested against captured responses. */
	static GeoAddress parseReverse(JsonNode root) {
		JsonNode a = root.path("address");
		if (a.isMissingNode() || a.isEmpty()) {
			throw new ServiceUnavailableException("No address was found at this location. "
					+ "Please enter it manually.");
		}

		// Nominatim names the locality differently by settlement size.
		String city = first(a, "city", "town", "village", "municipality", "state_district", "county");

		// Street and neighbourhood, the part Indian addresses call "area".
		// Administrative ward names ("Ward 106 Serilingampally") are left out:
		// accurate, but not what anyone writes on a parcel.
		Set<String> area = new LinkedHashSet<>();
		for (String field : List.of("road", "residential", "neighbourhood", "quarter", "suburb")) {
			String value = text(a, field);
			if (value != null && !value.toLowerCase().startsWith("ward ") && !value.equals(city)) {
				area.add(value);
			}
		}

		String postcode = text(a, "postcode");
		if (postcode != null) {
			postcode = postcode.replaceAll("\\s+", "");
		}

		return new GeoAddress(area.isEmpty() ? null : String.join(", ", area), city, text(a, "state"),
				postcode, text(root, "display_name"));
	}

	static Optional<PincodeInfo> parsePincode(String pincode, JsonNode root) {
		JsonNode first = root.isArray() && !root.isEmpty() ? root.get(0) : root;
		JsonNode offices = first.path("PostOffice");
		if (!"Success".equalsIgnoreCase(first.path("Status").asText()) || !offices.isArray() || offices.isEmpty()) {
			return Optional.empty();
		}

		List<String> areas = new ArrayList<>();
		for (JsonNode office : offices) {
			String name = text(office, "Name");
			if (name != null && !areas.contains(name)) {
				areas.add(name);
			}
		}
		JsonNode head = offices.get(0);
		return Optional.of(new PincodeInfo(pincode, text(head, "District"), text(head, "State"), areas));
	}

	private static String first(JsonNode node, String... fields) {
		for (String field : fields) {
			String value = text(node, field);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		String s = value.asText().strip();
		return s.isEmpty() ? null : s;
	}

	private static String round(double value) {
		return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).toPlainString();
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
