package com.jtspringproject.JtSpringProject.dto.response;

import java.math.BigDecimal;

import com.jtspringproject.JtSpringProject.models.OrderItem;

public class OrderItemResponse {

    private int id;
    private int productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public OrderItemResponse() {
    }

    public static OrderItemResponse from(OrderItem item) {
        if (item == null) {
            return null;
        }
        OrderItemResponse dto = new OrderItemResponse();
        dto.id = item.getId();
        if (item.getProduct() != null) {
            dto.productId = item.getProduct().getId();
            dto.productName = item.getProduct().getName();
        }
        dto.quantity = item.getQuantity();
        dto.unitPrice = item.getPrice();
        dto.lineTotal = item.getLineTotal();
        return dto;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}
