package com.jtspringproject.JtSpringProject.controller.api;

import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.RazorpayConfirmRequest;
import com.jtspringproject.JtSpringProject.dto.response.PaymentResponse;
import com.jtspringproject.JtSpringProject.models.Payment;
import com.jtspringproject.JtSpringProject.payment.RazorpayGateway;
import com.jtspringproject.JtSpringProject.payment.RazorpayPaymentService;
import com.jtspringproject.JtSpringProject.payment.RazorpayWebhookService;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;

import jakarta.validation.Valid;

/**
 * Razorpay checkout endpoints.
 *
 * <p>Three steps, and the trust boundary sits between the second and third:
 * the server creates a gateway order, the browser pays, and the server then
 * verifies what the browser claims happened. Nothing the browser sends is
 * believed without a signature computed from a secret it never sees.
 */
@RestController
@RequestMapping("/api/payments/razorpay")
public class RazorpayApiController {

	private static final Logger log = LoggerFactory.getLogger(RazorpayApiController.class);

	private final RazorpayPaymentService paymentService;
	private final RazorpayWebhookService webhookService;
	private final RazorpayGateway gateway;

	public RazorpayApiController(RazorpayPaymentService paymentService,
			RazorpayWebhookService webhookService, RazorpayGateway gateway) {
		this.paymentService = paymentService;
		this.webhookService = webhookService;
		this.gateway = gateway;
	}

	/** Lets the storefront hide online payment when no keys are configured. */
	@GetMapping("/config")
	public ResponseEntity<ApiResponse<Map<String, Object>>> config() {
		return ResponseEntity.ok(ApiResponse.success(Map.of(
				"enabled", gateway.isConfigured(),
				"keyId", gateway.isConfigured() ? gateway.getPublicKeyId() : "")));
	}

	/**
	 * Creates the gateway order the browser pays against.
	 *
	 * <p>The amount is read from our own order row rather than the request, so a
	 * modified client cannot choose its own price.
	 */
	@PostMapping("/orders/{orderId}")
	public ResponseEntity<ApiResponse<RazorpayPaymentService.CheckoutSession>> startPayment(
			@AuthenticationPrincipal AppUserDetails principal,
			@PathVariable int orderId) {
		return ResponseEntity.ok(ApiResponse.success("Payment session created",
				paymentService.startPayment(orderId, principal.getId(), principal.isAdmin())));
	}

	/** Verifies the signature the browser returns after paying. */
	@PostMapping("/confirm")
	public ResponseEntity<ApiResponse<PaymentResponse>> confirm(
			@AuthenticationPrincipal AppUserDetails principal,
			@Valid @RequestBody RazorpayConfirmRequest request) {

		Payment payment = paymentService.confirmPayment(
				request.getRazorpayOrderId(), request.getRazorpayPaymentId(),
				request.getRazorpaySignature(), principal.getId(), principal.isAdmin());

		return ResponseEntity.ok(ApiResponse.success("Payment confirmed", PaymentResponse.from(payment)));
	}

	/**
	 * Razorpay's server-to-server notification, and the authoritative one.
	 *
	 * <p>Unauthenticated by necessity - Razorpay holds no JWT - and protected
	 * instead by an HMAC signature over the request body. The body is taken as a
	 * raw String rather than a parsed object because re-serialising parsed JSON
	 * reorders keys and drops whitespace, and the signature would never match.
	 *
	 * <p>Always answers 200 once the signature is valid. A non-2xx makes
	 * Razorpay retry, and a payload this code cannot handle fails identically
	 * every time; the event is stored either way for reconciliation.
	 */
	@PostMapping("/webhook")
	public ResponseEntity<String> webhook(
			@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
			@RequestBody String rawBody) {

		if (!gateway.verifyWebhookSignature(rawBody, signature)) {
			// 401, not 400: this is an authentication failure, and anything
			// that cannot prove it is Razorpay must not reach the handlers.
			log.warn("Rejected webhook with invalid signature");
			return ResponseEntity.status(401).body("invalid signature");
		}

		JSONObject payload = new JSONObject(rawBody);
		String eventType = payload.optString("event", "unknown");
		// Razorpay's own id when present; otherwise derive a stable one so a
		// retry still collides with the original rather than being processed
		// a second time.
		String eventId = payload.optString("id", eventType + ":" + rawBody.hashCode());

		if (webhookService.recordEvent(eventId, eventType, rawBody)) {
			webhookService.process(eventId, eventType, payload);
		}
		return ResponseEntity.ok("ok");
	}
}
