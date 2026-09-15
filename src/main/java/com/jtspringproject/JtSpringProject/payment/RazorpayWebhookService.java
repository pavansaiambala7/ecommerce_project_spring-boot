package com.jtspringproject.JtSpringProject.payment;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.PaymentDao;
import com.jtspringproject.JtSpringProject.models.Payment;

/**
 * Handles Razorpay webhooks, which are the authoritative record of a payment.
 *
 * <p>The browser callback is a convenience: it tells us quickly, but it can be
 * closed, crash, or lose its network between paying and reporting back. The
 * webhook always arrives, so a customer whose browser died still gets the order
 * they paid for.
 *
 * <p>Razorpay delivers at least once and retries anything that is not answered
 * 2xx, so the same event will arrive again. Every event id is recorded before
 * it is acted on, and a duplicate is acknowledged without being reprocessed -
 * otherwise a retry could mark an order paid twice or issue a second refund.
 */
@Service
public class RazorpayWebhookService {

	private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);

	private final JdbcTemplate jdbc;
	private final PaymentDao paymentDao;
	private final RazorpayPaymentService paymentService;

	public RazorpayWebhookService(JdbcTemplate jdbc, PaymentDao paymentDao,
			RazorpayPaymentService paymentService) {
		this.jdbc = jdbc;
		this.paymentDao = paymentDao;
		this.paymentService = paymentService;
	}

	/**
	 * @return true when the event was newly recorded and should be processed,
	 *         false when it is a duplicate that has already been handled
	 */
	@Transactional
	public boolean recordEvent(String eventId, String eventType, String rawBody) {
		try {
			jdbc.update("INSERT INTO razorpay_webhook_event (event_id, event_type, payload) VALUES (?, ?, ?)",
					eventId, eventType, rawBody);
			return true;
		} catch (DuplicateKeyException e) {
			log.info("Ignoring duplicate webhook event {}", eventId);
			return false;
		}
	}

	@Transactional
	public void process(String eventId, String eventType, JSONObject payload) {
		try {
			switch (eventType) {
				case "payment.captured", "order.paid" -> handleCaptured(payload);
				case "payment.failed" -> handleFailed(payload);
				case "refund.processed" -> handleRefunded(payload);
				default -> log.debug("No handler for webhook event type {}", eventType);
			}
			jdbc.update("UPDATE razorpay_webhook_event SET processed_at = CURRENT_TIMESTAMP WHERE event_id = ?",
					eventId);
		} catch (Exception e) {
			// Recorded rather than rethrown. Answering non-2xx would make
			// Razorpay retry, and a payload this code cannot handle will fail
			// identically every time - the retries would achieve nothing except
			// noise. The stored error is what reconciliation looks at.
			log.error("Failed to process webhook event {} ({})", eventId, eventType, e);
			jdbc.update("UPDATE razorpay_webhook_event SET error = ? WHERE event_id = ?",
					e.getMessage(), eventId);
		}
	}

	private void handleCaptured(JSONObject payload) {
		JSONObject entity = paymentEntity(payload);
		if (entity == null) {
			return;
		}
		String razorpayPaymentId = entity.optString("id", null);
		String razorpayOrderId = entity.optString("order_id", null);
		if (razorpayOrderId == null) {
			return;
		}

		paymentDao.findByRazorpayOrderId(razorpayOrderId).ifPresentOrElse(payment -> {
			// No signature here: the webhook's own signature was already
			// verified against the raw body before this method ran, which is a
			// stronger guarantee than the per-payment one.
			paymentService.settle(payment, razorpayPaymentId, "webhook");
		}, () -> log.warn("Webhook for unknown gateway order {}", razorpayOrderId));
	}

	private void handleFailed(JSONObject payload) {
		JSONObject entity = paymentEntity(payload);
		if (entity == null) {
			return;
		}
		String razorpayOrderId = entity.optString("order_id", null);
		if (razorpayOrderId != null) {
			String reason = entity.optString("error_description", "Payment failed at gateway");
			paymentService.markFailed(razorpayOrderId, reason);
		}
	}

	private void handleRefunded(JSONObject payload) {
		JSONObject entity = payload.optJSONObject("payload") == null ? null
				: payload.getJSONObject("payload").optJSONObject("refund");
		if (entity == null) {
			return;
		}
		String razorpayPaymentId = entity.getJSONObject("entity").optString("payment_id", null);
		if (razorpayPaymentId == null) {
			return;
		}
		paymentDao.findByRazorpayPaymentId(razorpayPaymentId).ifPresent(payment -> {
			payment.setStatus(Payment.PaymentStatus.REFUNDED);
			paymentDao.save(payment);
			log.info("Payment {} marked refunded by webhook", razorpayPaymentId);
		});
	}

	/** Razorpay nests the useful object at payload.payment.entity. */
	private JSONObject paymentEntity(JSONObject payload) {
		JSONObject outer = payload.optJSONObject("payload");
		if (outer == null) {
			return null;
		}
		JSONObject payment = outer.optJSONObject("payment");
		return payment == null ? null : payment.optJSONObject("entity");
	}
}
