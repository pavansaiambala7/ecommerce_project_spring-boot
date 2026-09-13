package com.jtspringproject.JtSpringProject.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.jtspringproject.JtSpringProject.models.Payment;

public class PaymentResponse {

    private int id;
    private int orderId;
    private BigDecimal amount;
    private String method;
    private String status;
    private String transactionId;
    private LocalDateTime createdAt;

    public PaymentResponse() {
    }

    public static PaymentResponse from(Payment payment) {
        if (payment == null) {
            return null;
        }
        PaymentResponse dto = new PaymentResponse();
        dto.id = payment.getId();
        if (payment.getOrder() != null) {
            dto.orderId = payment.getOrder().getId();
        }
        dto.amount = payment.getAmount();
        dto.method = payment.getMethod() == null ? null : payment.getMethod().name();
        dto.status = payment.getStatus() == null ? null : payment.getStatus().name();
        dto.transactionId = payment.getTransactionId();
        dto.createdAt = payment.getCreatedAt();
        return dto;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
