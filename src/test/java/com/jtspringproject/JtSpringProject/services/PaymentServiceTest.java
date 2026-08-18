package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jtspringproject.JtSpringProject.dao.PaymentDao;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.Payment;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentDao paymentDao;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentService paymentService;

    private Order testOrder;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setTotalAmount(25.0);
        testOrder.setStatus(Order.OrderStatus.CREATED);

        testPayment = new Payment();
        testPayment.setId(1);
        testPayment.setOrder(testOrder);
        testPayment.setAmount(25.0);
        testPayment.setMethod(Payment.PaymentMethod.CARD);
        testPayment.setStatus(Payment.PaymentStatus.SUCCESS);
    }

    @Test
    void processPayment_shouldProcessAndReturnPayment() {
        when(orderService.getOrderById(1)).thenReturn(testOrder);
        when(paymentDao.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(orderService.updateOrderStatus(eq(1), eq(Order.OrderStatus.PAID))).thenReturn(testOrder);

        Payment result = paymentService.processPayment(1, Payment.PaymentMethod.CARD);

        assertNotNull(result);
        assertEquals(Payment.PaymentStatus.SUCCESS, result.getStatus());
        assertEquals(25.0, result.getAmount());
        assertNotNull(result.getTransactionId());
    }

    @Test
    void processPayment_shouldThrowWhenOrderNotFound() {
        when(orderService.getOrderById(999)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                paymentService.processPayment(999, Payment.PaymentMethod.COD));
    }

    @Test
    void processPayment_shouldThrowWhenOrderNotPayable() {
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderService.getOrderById(1)).thenReturn(testOrder);

        assertThrows(IllegalStateException.class, () ->
                paymentService.processPayment(1, Payment.PaymentMethod.CARD));
    }

    @Test
    void getPaymentByOrder_shouldReturnPaymentWhenExists() {
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));

        Payment result = paymentService.getPaymentByOrder(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void getPaymentByOrder_shouldReturnNullWhenNotExists() {
        when(paymentDao.findByOrderId(999)).thenReturn(Optional.empty());

        Payment result = paymentService.getPaymentByOrder(999);

        assertNull(result);
    }

    @Test
    void refundPayment_shouldRefundSuccessfulPayment() {
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));
        when(paymentDao.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.refundPayment(1);

        assertEquals(Payment.PaymentStatus.REFUNDED, result.getStatus());
        verify(orderService).cancelOrder(1);
    }

    @Test
    void refundPayment_shouldThrowWhenPaymentNotSuccessful() {
        testPayment.setStatus(Payment.PaymentStatus.FAILED);
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));

        assertThrows(IllegalStateException.class, () ->
                paymentService.refundPayment(1));
    }
}
