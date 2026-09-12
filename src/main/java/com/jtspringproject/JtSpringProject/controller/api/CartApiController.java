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
import com.jtspringproject.JtSpringProject.dto.response.CartResponse;
import com.jtspringproject.JtSpringProject.dto.response.OrderResponse;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;
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

    public CartApiController(cartService cartService) {
        this.cartService = cartService;
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

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @AuthenticationPrincipal AppUserDetails principal) {
        Order order = cartService.checkout(principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed", OrderResponse.from(order)));
    }
}
