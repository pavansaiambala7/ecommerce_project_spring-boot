package com.jtspringproject.JtSpringProject.controller.api;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.PaymentRequest;
import com.jtspringproject.JtSpringProject.dto.response.PaymentResponse;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.idempotency.IdempotencyService;
import com.jtspringproject.JtSpringProject.models.Payment;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;
import com.jtspringproject.JtSpringProject.services.PaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentApiController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    public PaymentApiController(PaymentService paymentService, IdempotencyService idempotencyService) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Takes payment for an order.
     *
     * <p>The most important endpoint in the application to make retry-safe: a
     * repeat here charges a customer twice. Callers should send an
     * {@code Idempotency-Key}; a repeat of that key replays the original
     * response instead of taking payment again.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        Optional<String> key = idempotencyService.validate(idempotencyKey);
        if (key.isEmpty()) {
            return pay(principal, request);
        }

        // The order id is part of the fingerprint, so reusing one key across two
        // different orders is rejected rather than silently replaying the first.
        return idempotencyService.run(key.get(), principal.getId(), "POST /api/payments",
                String.valueOf(request.getOrderId()), () -> pay(principal, request));
    }

    private ResponseEntity<ApiResponse<PaymentResponse>> pay(AppUserDetails principal,
            PaymentRequest request) {
        Payment payment = paymentService.processPayment(request.getOrderId(), request.getMethod(),
                principal.getId(), principal.isAdmin());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment processed successfully", PaymentResponse.from(payment)));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable int orderId) {
        Payment payment = paymentService.getPaymentByOrder(orderId, principal.getId(), principal.isAdmin());
        if (payment == null) {
            throw new ResourceNotFoundException("No payment found for order: " + orderId);
        }
        return ResponseEntity.ok(ApiResponse.success(PaymentResponse.from(payment)));
    }

    /** Refunds are an administrative action; the security chain also restricts this path. */
    @PostMapping("/refund/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(@PathVariable int orderId) {
        Payment payment = paymentService.refundPayment(orderId);
        return ResponseEntity.ok(
                ApiResponse.success("Payment refunded successfully", PaymentResponse.from(payment)));
    }
}
