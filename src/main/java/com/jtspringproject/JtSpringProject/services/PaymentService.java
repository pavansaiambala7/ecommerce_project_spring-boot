package com.jtspringproject.JtSpringProject.services;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.OrderDao;
import com.jtspringproject.JtSpringProject.dao.PaymentDao;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.Payment;

@Service
public class PaymentService {

    private final PaymentDao paymentDao;
    private final OrderDao orderDao;
    private final OrderService orderService;

    public PaymentService(PaymentDao paymentDao, OrderDao orderDao, OrderService orderService) {
        this.paymentDao = paymentDao;
        this.orderDao = orderDao;
        this.orderService = orderService;
    }

    @Transactional
    public Payment processPayment(int orderId, Payment.PaymentMethod method, int callerId, boolean callerIsAdmin) {
        Order order = orderDao.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        assertVisible(order, callerId, callerIsAdmin);

        if (order.getStatus() != Order.OrderStatus.CREATED) {
            throw new BusinessRuleException("Order is not in a payable state: " + order.getStatus() + ".");
        }
        if (paymentDao.findByOrderId(orderId).isPresent()) {
            throw new BusinessRuleException("Order " + orderId + " has already been paid.");
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(method);
        payment.setTransactionId(UUID.randomUUID().toString());

        // A real gateway call belongs here. It would throw on failure, which rolls
        // the transaction back; the previous try/catch wrapped two setters that
        // could not throw and set a FAILED status the same transaction discarded.
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        order.setStatus(Order.OrderStatus.PAID);
        orderDao.save(order);

        return paymentDao.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByOrder(int orderId, int callerId, boolean callerIsAdmin) {
        Payment payment = paymentDao.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            return null;
        }
        assertVisible(payment.getOrder(), callerId, callerIsAdmin);
        return payment;
    }

    /**
     * Refunds a successful payment and returns the stock.
     *
     * <p>This no longer delegates to {@code cancelOrder}, which refuses SHIPPED and
     * DELIVERED orders - precisely the ones that get refunded - and which overwrote
     * the outcome with CANCELLED, losing the fact that money was returned.
     */
    @Transactional
    public Payment refundPayment(int orderId) {
        Payment payment = paymentDao.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));

        if (payment.getStatus() != Payment.PaymentStatus.SUCCESS) {
            throw new BusinessRuleException("Cannot refund a payment with status " + payment.getStatus() + ".");
        }

        Order order = payment.getOrder();
        if (order.getStatus().holdsStock()) {
            orderService.restoreStock(order);
        }

        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        order.setStatus(Order.OrderStatus.REFUNDED);
        orderDao.save(order);

        return paymentDao.save(payment);
    }

    private void assertVisible(Order order, int callerId, boolean callerIsAdmin) {
        if (!callerIsAdmin && order.getCustomer().getId() != callerId) {
            throw new AccessDeniedException("You do not have access to this order.");
        }
    }
}
