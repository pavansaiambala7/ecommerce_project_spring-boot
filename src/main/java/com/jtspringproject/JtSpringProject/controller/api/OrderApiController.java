package com.jtspringproject.JtSpringProject.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.OrderRequest;
import com.jtspringproject.JtSpringProject.dto.response.OrderResponse;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.OrderItem;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;
import com.jtspringproject.JtSpringProject.services.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderApiController {

    private final OrderService orderService;

    public OrderApiController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Places an order for the authenticated caller.
     *
     * <p>The owning user comes from the principal. This used to take a
     * {@code userId} query parameter on an unauthenticated endpoint, so anyone
     * could create orders in anyone else's name.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody OrderRequest request) {

        List<OrderItem> items = request.getItems().stream().map(line -> {
            OrderItem item = new OrderItem();
            Product product = new Product();
            product.setId(line.getProductId());
            item.setProduct(product);
            item.setQuantity(line.getQuantity());
            return item;
        }).toList();

        Order order = orderService.createOrder(principal.getId(), items);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", OrderResponse.from(order)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable int id) {
        Order order = orderService.getOrderForCaller(id, principal.getId(), principal.isAdmin());
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.from(order)));
    }

    /** Returns the caller's own orders. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal AppUserDetails principal) {
        List<OrderResponse> orders = orderService.getOrdersByUser(principal.getId()).stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == principal.id")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByUser(@PathVariable int userId) {
        List<OrderResponse> orders = orderService.getOrdersByUser(userId).stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable int id,
            @RequestParam Order.OrderStatus status) {
        Order order = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Order status updated", OrderResponse.from(order)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable int id) {
        Order order = orderService.cancelOrder(id, principal.getId(), principal.isAdmin());
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", OrderResponse.from(order)));
    }
}
