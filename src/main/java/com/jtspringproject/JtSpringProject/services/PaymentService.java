package com.jtspringproject.JtSpringProject.services;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.PaymentDao;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.Payment;

@Service
public class PaymentService {

    private final PaymentDao paymentDao;
    private final OrderService orderService;

    public PaymentService(PaymentDao paymentDao, OrderService orderService) {
        this.paymentDao = paymentDao;
        this.orderService = orderService;
    }

    @Transactional
    public Payment processPayment(int orderId, Payment.PaymentMethod method) {
        Order order = orderService.getOrderById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        if (order.getStatus() != Order.OrderStatus.CREATED) {
            throw new IllegalStateException("Order is not in a payable state: " + order.getStatus());
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(method);
        payment.setTransactionId(UUID.randomUUID().toString());

        // Simulate payment processing
        try {
            // In production, integrate with actual payment gateway
            payment.setStatus(Payment.PaymentStatus.SUCCESS);
            orderService.updateOrderStatus(orderId, Order.OrderStatus.PAID);
        } catch (Exception e) {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            orderService.updateOrderStatus(orderId, Order.OrderStatus.FAILED);
        }

        return paymentDao.save(payment);
    }

    public Payment getPaymentByOrder(int orderId) {
        return paymentDao.findByOrderId(orderId).orElse(null);
    }

    @Transactional
    public Payment refundPayment(int orderId) {
        Payment payment = paymentDao.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));

        if (payment.getStatus() != Payment.PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Cannot refund payment with status: " + payment.getStatus());
        }

        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        orderService.cancelOrder(orderId);

        return paymentDao.save(payment);
    }
}
