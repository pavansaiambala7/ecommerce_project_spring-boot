package com.jtspringproject.JtSpringProject.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.jtspringproject.JtSpringProject.models.Order;

/**
 * Public view of an order.
 *
 * <p>Flattening items into their own DTO breaks the Order / OrderItem / Order
 * cycle. Serialising the entities directly sent Jackson into unbounded recursion,
 * so this endpoint could never return successfully.
 */
public class OrderResponse {

    private int id;
    private int customerId;
    private String customerUsername;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponse> items;
    private AddressResponse shippingAddress;

    public OrderResponse() {
    }

    public static OrderResponse from(Order order) {
        if (order == null) {
            return null;
        }
        OrderResponse dto = new OrderResponse();
        dto.id = order.getId();
        if (order.getCustomer() != null) {
            dto.customerId = order.getCustomer().getId();
            dto.customerUsername = order.getCustomer().getUsername();
        }
        dto.status = order.getStatus() == null ? null : order.getStatus().name();
        dto.totalAmount = order.getTotalAmount();
        dto.createdAt = order.getCreatedAt();
        dto.updatedAt = order.getUpdatedAt();
        dto.items = order.getItems().stream().map(OrderItemResponse::from).toList();
        dto.shippingAddress = AddressResponse.from(order.getShippingAddress());
        return dto;
    }

    public AddressResponse getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(AddressResponse shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerUsername() {
        return customerUsername;
    }

    public void setCustomerUsername(String customerUsername) {
        this.customerUsername = customerUsername;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponse> items) {
        this.items = items;
    }
}
