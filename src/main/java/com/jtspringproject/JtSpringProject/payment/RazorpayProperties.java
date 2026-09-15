package com.jtspringproject.JtSpringProject.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Razorpay credentials, supplied from SSM like every other secret.
 *
 * <p>All three are blank by default so the application still starts without
 * them - useful for local work and for the test suite. {@link #isConfigured()}
 * is what the payment service checks before attempting a gateway call, so an
 * unconfigured deployment fails with a clear message rather than a null
 * pointer somewhere inside the SDK.
 */
@Component
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayProperties {

	/** Public key id (rzp_test_... or rzp_live_...). Safe to send to the browser. */
	private String keyId = "";

	/** Secret. Signs and verifies payments; must never reach the browser. */
	private String keySecret = "";

	/**
	 * Webhook signing secret. Separate from the key secret because webhooks are
	 * verified against a value you set in the Razorpay dashboard, and rotating
	 * one should not invalidate the other.
	 */
	private String webhookSecret = "";

	/** ISO currency for gateway orders. Razorpay accounts are currency-scoped. */
	private String currency = "INR";

	public boolean isConfigured() {
		return !keyId.isBlank() && !keySecret.isBlank();
	}

	public boolean isWebhookConfigured() {
		return !webhookSecret.isBlank();
	}

	public String getKeyId() {
		return keyId;
	}

	public void setKeyId(String keyId) {
		this.keyId = keyId == null ? "" : keyId.trim();
	}

	public String getKeySecret() {
		return keySecret;
	}

	public void setKeySecret(String keySecret) {
		this.keySecret = keySecret == null ? "" : keySecret.trim();
	}

	public String getWebhookSecret() {
		return webhookSecret;
	}

	public void setWebhookSecret(String webhookSecret) {
		this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}
}
