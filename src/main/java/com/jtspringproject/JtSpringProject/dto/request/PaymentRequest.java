package com.jtspringproject.JtSpringProject.dto.request;

import com.jtspringproject.JtSpringProject.models.Payment;

import jakarta.validation.constraints.NotNull;

public class PaymentRequest {

    @NotNull(message = "Order id is required")
    private Integer orderId;

    @NotNull(message = "Payment method is required (COD, CARD or UPI)")
    private Payment.PaymentMethod method;

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public Payment.PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(Payment.PaymentMethod method) {
        this.method = method;
    }
}
