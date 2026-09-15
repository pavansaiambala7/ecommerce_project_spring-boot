package com.jtspringproject.JtSpringProject.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;

/**
 * Signature verification is the only thing standing between a real payment and
 * an invented one, so it is tested directly rather than through the gateway.
 */
class RazorpayGatewayTest {

	private static final String KEY_SECRET = "test_secret_do_not_use_in_production";
	private static final String WEBHOOK_SECRET = "webhook_secret_value";

	private RazorpayGateway gateway;

	@BeforeEach
	void setUp() {
		RazorpayProperties properties = new RazorpayProperties();
		properties.setKeyId("rzp_test_example");
		properties.setKeySecret(KEY_SECRET);
		properties.setWebhookSecret(WEBHOOK_SECRET);
		gateway = new RazorpayGateway(properties);
	}

	/** Reproduces the signature Razorpay would send, to test against. */
	private static String sign(String payload, String secret) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		StringBuilder hex = new StringBuilder();
		for (byte b : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}

	@Test
	void acceptsAGenuineSignature() throws Exception {
		String orderId = "order_ABC123";
		String paymentId = "pay_XYZ789";
		String signature = sign(orderId + "|" + paymentId, KEY_SECRET);

		assertTrue(gateway.verifyPaymentSignature(orderId, paymentId, signature));
	}

	@Test
	void rejectsAForgedSignature() {
		// What an attacker can actually do: invent plausible ids and guess.
		assertFalse(gateway.verifyPaymentSignature("order_ABC123", "pay_XYZ789", "deadbeef"));
	}

	@Test
	void rejectsASignatureForADifferentPayment() throws Exception {
		// A signature genuinely issued by Razorpay, replayed against another
		// order. Without binding both ids into the signed payload this would
		// let one real payment settle any number of orders.
		String genuine = sign("order_ABC123|pay_XYZ789", KEY_SECRET);

		assertFalse(gateway.verifyPaymentSignature("order_DIFFERENT", "pay_XYZ789", genuine));
		assertFalse(gateway.verifyPaymentSignature("order_ABC123", "pay_DIFFERENT", genuine));
	}

	@Test
	void rejectsASignatureMadeWithTheWrongSecret() throws Exception {
		String wrong = sign("order_ABC123|pay_XYZ789", "someone_elses_secret");
		assertFalse(gateway.verifyPaymentSignature("order_ABC123", "pay_XYZ789", wrong));
	}

	@Test
	void rejectsMissingValuesRatherThanThrowing() {
		assertFalse(gateway.verifyPaymentSignature(null, "pay_1", "sig"));
		assertFalse(gateway.verifyPaymentSignature("order_1", null, "sig"));
		assertFalse(gateway.verifyPaymentSignature("order_1", "pay_1", null));
	}

	@Test
	void verifiesWebhookAgainstTheExactRawBody() throws Exception {
		String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_1\"}}}}";

		assertTrue(gateway.verifyWebhookSignature(body, sign(body, WEBHOOK_SECRET)));

		// One byte different - which is what re-serialising parsed JSON would
		// produce - and the signature must no longer match.
		assertFalse(gateway.verifyWebhookSignature(body + " ", sign(body, WEBHOOK_SECRET)));
	}

	@Test
	void webhookAndPaymentSecretsAreNotInterchangeable() throws Exception {
		String body = "{\"event\":\"payment.captured\"}";
		assertFalse(gateway.verifyWebhookSignature(body, sign(body, KEY_SECRET)));
	}

	@Test
	void convertsRupeesToPaiseWithoutFloatingPointDrift() {
		// 19.99 has no exact binary representation; doing this with doubles
		// gives 1998.9999... and truncates to a paisa short.
		assertEquals(1999L, gateway.toMinorUnits(new BigDecimal("19.99")));
		assertEquals(100L, gateway.toMinorUnits(new BigDecimal("1.00")));
		assertEquals(1L, gateway.toMinorUnits(new BigDecimal("0.01")));
		assertEquals(123456789L, gateway.toMinorUnits(new BigDecimal("1234567.89")));
	}

	@Test
	void refusesGatewayCallsWhenNotConfigured() {
		RazorpayGateway unconfigured = new RazorpayGateway(new RazorpayProperties());

		assertFalse(unconfigured.isConfigured());
		assertThrows(BusinessRuleException.class,
				() -> unconfigured.createOrder(1, new BigDecimal("10.00")));
	}

	@Test
	void rejectsWebhooksWhenNoWebhookSecretIsSet() {
		RazorpayProperties noWebhook = new RazorpayProperties();
		noWebhook.setKeyId("rzp_test_example");
		noWebhook.setKeySecret(KEY_SECRET);

		// Failing open here would accept any unsigned webhook as genuine.
		assertFalse(new RazorpayGateway(noWebhook).verifyWebhookSignature("{}", "anything"));
	}
}
