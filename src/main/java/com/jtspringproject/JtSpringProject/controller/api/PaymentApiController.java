package com.jtspringproject.JtSpringProject.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.PaymentRequest;
import com.jtspringproject.JtSpringProject.dto.response.PaymentResponse;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Payment;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;
import com.jtspringproject.JtSpringProject.services.PaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentApiController {

    private final PaymentService paymentService;

    public PaymentApiController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody PaymentRequest request) {
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
