package com.jtspringproject.JtSpringProject.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

/**
 * Talks to Razorpay and decides whether a payment claim is genuine.
 *
 * <p>The security model rests on one fact: the key secret is known only to this
 * server and to Razorpay. Everything the browser reports back is signed with
 * it, so a tampered amount or an invented payment id fails verification. The
 * browser is never trusted to state whether a payment succeeded.
 */
@Component
public class RazorpayGateway {

	private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

	/** Razorpay works in the smallest currency unit - paise for INR. */
	private static final BigDecimal MINOR_UNITS = new BigDecimal("100");

	private final RazorpayProperties properties;

	public RazorpayGateway(RazorpayProperties properties) {
		this.properties = properties;
	}

	/** Creates a gateway order and returns its id for the browser to pay against. */
	public String createOrder(int orderId, BigDecimal amount) {
		requireConfigured();
		try {
			JSONObject request = new JSONObject();
			request.put("amount", toMinorUnits(amount));
			request.put("currency", properties.getCurrency());
			// Our own order id, echoed back on every webhook. It is how a
			// gateway event is matched to a row in this database.
			request.put("receipt", "order_" + orderId);
			request.put("payment_capture", true);

			RazorpayClient client = new RazorpayClient(properties.getKeyId(), properties.getKeySecret());
			return client.orders.create(request).get("id");
		} catch (RazorpayException e) {
			log.error("Razorpay order creation failed for order {}", orderId, e);
			throw new BusinessRuleException("Could not start payment. Please try again.");
		}
	}

	/**
	 * Verifies a payment the browser claims succeeded.
	 *
	 * <p>Razorpay signs {@code order_id|payment_id} with the key secret. Anyone
	 * can invent those two ids; nobody without the secret can produce a matching
	 * signature, which is what makes this check meaningful rather than
	 * decorative.
	 */
	public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String signature) {
		requireConfigured();
		if (razorpayOrderId == null || razorpayPaymentId == null || signature == null) {
			return false;
		}
		String expected = hmacSha256(razorpayOrderId + "|" + razorpayPaymentId, properties.getKeySecret());
		return constantTimeEquals(expected, signature);
	}

	/**
	 * Verifies a webhook against the raw request body.
	 *
	 * <p>It must be the raw bytes: re-serialising the parsed JSON changes key
	 * order and whitespace, and the signature would never match again.
	 */
	public boolean verifyWebhookSignature(String rawBody, String signature) {
		if (!properties.isWebhookConfigured()) {
			log.error("Webhook received but no webhook secret is configured - rejecting");
			return false;
		}
		if (rawBody == null || signature == null) {
			return false;
		}
		return constantTimeEquals(hmacSha256(rawBody, properties.getWebhookSecret()), signature);
	}

	/** Refunds a captured payment in full. */
	public void refund(String razorpayPaymentId) {
		requireConfigured();
		try {
			RazorpayClient client = new RazorpayClient(properties.getKeyId(), properties.getKeySecret());
			JSONObject request = new JSONObject();
			request.put("speed", "normal");
			client.payments.refund(razorpayPaymentId, request);
		} catch (RazorpayException e) {
			log.error("Razorpay refund failed for payment {}", razorpayPaymentId, e);
			throw new BusinessRuleException("Refund could not be processed by the gateway.");
		}
	}

	/**
	 * Converts rupees to paise.
	 *
	 * <p>Deliberately not {@code amount.doubleValue() * 100}: binary floating
	 * point cannot represent most decimal currency values, and rounding the
	 * result is how a charge ends up a paisa short of the order total.
	 */
	public long toMinorUnits(BigDecimal amount) {
		return amount.multiply(MINOR_UNITS).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	public String getPublicKeyId() {
		return properties.getKeyId();
	}

	public boolean isConfigured() {
		return properties.isConfigured();
	}

	private void requireConfigured() {
		if (!properties.isConfigured()) {
			throw new BusinessRuleException(
					"Online payment is not available. Please choose cash on delivery.");
		}
	}

	private static String hmacSha256(String payload, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (Exception e) {
			throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
		}
	}

	/**
	 * Compares in constant time. A normal string comparison returns as soon as
	 * two characters differ, and the timing of those returns leaks how much of a
	 * guessed signature was correct, one character at a time.
	 */
	private static boolean constantTimeEquals(String a, String b) {
		return MessageDigest.isEqual(
				a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}
}
