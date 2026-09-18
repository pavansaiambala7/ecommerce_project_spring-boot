package com.jtspringproject.JtSpringProject.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.CartItemRequest;
import com.jtspringproject.JtSpringProject.dto.request.CartQuantityRequest;
import com.jtspringproject.JtSpringProject.dto.request.CheckoutRequest;
import java.util.Optional;

import org.springframework.web.bind.annotation.RequestHeader;

import com.jtspringproject.JtSpringProject.dto.response.CartResponse;
import com.jtspringproject.JtSpringProject.dto.response.OrderResponse;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;
import com.jtspringproject.JtSpringProject.idempotency.IdempotencyService;
import com.jtspringproject.JtSpringProject.services.cartService;

import jakarta.validation.Valid;

/**
 * Shopping cart API. Every operation acts on the authenticated caller's own
 * cart; there is no path that names another user's cart.
 */
@RestController
@RequestMapping("/api/cart")
public class CartApiController {

    private final cartService cartService;
    private final IdempotencyService idempotencyService;

    public CartApiController(cartService cartService, IdempotencyService idempotencyService) {
        this.cartService = cartService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
                CartResponse.from(cartService.getOrCreateCart(principal.getId()))));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody CartItemRequest request) {
        cartService.addItem(principal.getId(), request.getProductId(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Item added to cart",
                CartResponse.from(cartService.getOrCreateCart(principal.getId()))));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable int productId,
            @Valid @RequestBody CartQuantityRequest request) {
        cartService.updateQuantity(principal.getId(), productId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Cart updated",
                CartResponse.from(cartService.getOrCreateCart(principal.getId()))));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable int productId) {
        cartService.removeItem(principal.getId(), productId);
        return ResponseEntity.ok(ApiResponse.success("Item removed",
                CartResponse.from(cartService.getOrCreateCart(principal.getId()))));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal AppUserDetails principal) {
        cartService.clear(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Cart cleared", null));
    }

    /**
     * Turns the cart into an order.
     *
     * <p>Safe to retry when the caller sends an {@code Idempotency-Key}. Without
     * one a double-tap, an impatient second click, or a proxy retrying a request
     * whose response it never saw each create a second order and decrement stock
     * twice. The header is optional so existing clients keep working, but the
     * storefront always sends it.
     */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {

        int addressId = request.getAddressId();
        Optional<String> key = idempotencyService.validate(idempotencyKey);
        if (key.isEmpty()) {
            return placeOrder(principal.getId(), addressId);
        }

        // The address is part of the request fingerprint: reusing a key to ship
        // the same cart somewhere else is a different request, and must be
        // rejected rather than replaying an order bound for the first address.
        return idempotencyService.run(key.get(), principal.getId(), "POST /api/cart/checkout",
                "addressId=" + addressId, () -> placeOrder(principal.getId(), addressId));
    }

    private ResponseEntity<ApiResponse<OrderResponse>> placeOrder(int customerId, int addressId) {
        Order order = cartService.checkout(customerId, addressId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed", OrderResponse.from(order)));
    }
}
