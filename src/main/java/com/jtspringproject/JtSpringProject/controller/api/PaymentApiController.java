package com.jtspringproject.JtSpringProject.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.models.Payment;
import com.jtspringproject.JtSpringProject.services.PaymentService;

@RestController
@RequestMapping("/api/payments")
public class PaymentApiController {

    private final PaymentService paymentService;

    public PaymentApiController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Payment>> processPayment(
            @RequestParam int orderId,
            @RequestParam Payment.PaymentMethod method) {
        try {
            Payment payment = paymentService.processPayment(orderId, method);
            return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", payment));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<Payment>> getPaymentByOrder(@PathVariable int orderId) {
        Payment payment = paymentService.getPaymentByOrder(orderId);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @PostMapping("/refund/{orderId}")
    public ResponseEntity<ApiResponse<Payment>> refundPayment(@PathVariable int orderId) {
        try {
            Payment payment = paymentService.refundPayment(orderId);
            return ResponseEntity.ok(ApiResponse.success("Payment refunded successfully", payment));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
