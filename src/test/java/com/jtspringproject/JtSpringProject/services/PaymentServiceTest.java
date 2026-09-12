package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.jtspringproject.JtSpringProject.dao.OrderDao;
import com.jtspringproject.JtSpringProject.dao.PaymentDao;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.Payment;
import com.jtspringproject.JtSpringProject.models.User;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final int OWNER_ID = 7;
    private static final int OTHER_USER_ID = 99;

    @Mock
    private PaymentDao paymentDao;

    @Mock
    private OrderDao orderDao;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentService paymentService;

    private Order testOrder;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setUsername("owner");

        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setCustomer(owner);
        testOrder.setTotalAmount(new BigDecimal("25.00"));
        testOrder.setStatus(Order.OrderStatus.CREATED);

        testPayment = new Payment();
        testPayment.setId(1);
        testPayment.setOrder(testOrder);
        testPayment.setAmount(new BigDecimal("25.00"));
        testPayment.setMethod(Payment.PaymentMethod.CARD);
        testPayment.setStatus(Payment.PaymentStatus.SUCCESS);
    }

    @Test
    void processPayment_shouldProcessAndReturnPayment() {
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.empty());
        when(paymentDao.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.processPayment(1, Payment.PaymentMethod.CARD, OWNER_ID, false);

        assertNotNull(result);
        assertEquals(Payment.PaymentStatus.SUCCESS, result.getStatus());
        assertEquals(0, new BigDecimal("25.00").compareTo(result.getAmount()));
        assertNotNull(result.getTransactionId());
        assertEquals(Order.OrderStatus.PAID, testOrder.getStatus());
    }

    @Test
    void processPayment_shouldThrowWhenOrderNotFound() {
        when(orderDao.findWithItemsById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                paymentService.processPayment(999, Payment.PaymentMethod.COD, OWNER_ID, false));
    }

    @Test
    void processPayment_shouldThrowWhenOrderNotPayable() {
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));

        assertThrows(BusinessRuleException.class, () ->
                paymentService.processPayment(1, Payment.PaymentMethod.CARD, OWNER_ID, false));
    }

    @Test
    void processPayment_shouldRejectPayingSomeoneElsesOrder() {
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));

        assertThrows(AccessDeniedException.class, () ->
                paymentService.processPayment(1, Payment.PaymentMethod.CARD, OTHER_USER_ID, false));

        verify(paymentDao, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_shouldRejectDoublePayment() {
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));

        assertThrows(BusinessRuleException.class, () ->
                paymentService.processPayment(1, Payment.PaymentMethod.CARD, OWNER_ID, false));
    }

    @Test
    void getPaymentByOrder_shouldReturnPaymentWhenExists() {
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));

        Payment result = paymentService.getPaymentByOrder(1, OWNER_ID, false);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void getPaymentByOrder_shouldReturnNullWhenNotExists() {
        when(paymentDao.findByOrderId(999)).thenReturn(Optional.empty());

        Payment result = paymentService.getPaymentByOrder(999, OWNER_ID, false);

        assertNull(result);
    }

    @Test
    void getPaymentByOrder_shouldRejectOtherUsersPayment() {
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));

        assertThrows(AccessDeniedException.class, () ->
                paymentService.getPaymentByOrder(1, OTHER_USER_ID, false));
    }

    @Test
    void getPaymentByOrder_shouldAllowAdmin() {
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));

        assertNotNull(paymentService.getPaymentByOrder(1, OTHER_USER_ID, true));
    }

    @Test
    void refundPayment_shouldRefundAndRestoreStock() {
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));
        when(paymentDao.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.refundPayment(1);

        assertEquals(Payment.PaymentStatus.REFUNDED, result.getStatus());
        // The order is marked REFUNDED, not CANCELLED: cancelling lost the fact
        // that money had been returned.
        assertEquals(Order.OrderStatus.REFUNDED, testOrder.getStatus());
        verify(orderService).restoreStock(testOrder);
    }

    /**
     * Regression: refunds used to delegate to cancelOrder, which rejected SHIPPED
     * and DELIVERED orders outright, making the most common refund impossible.
     */
    @Test
    void refundPayment_shouldRefundDeliveredOrder() {
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));
        when(paymentDao.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.refundPayment(1);

        assertEquals(Payment.PaymentStatus.REFUNDED, result.getStatus());
        assertEquals(Order.OrderStatus.REFUNDED, testOrder.getStatus());
    }

    @Test
    void refundPayment_shouldThrowWhenPaymentNotSuccessful() {
        testPayment.setStatus(Payment.PaymentStatus.FAILED);
        when(paymentDao.findByOrderId(1)).thenReturn(Optional.of(testPayment));

        assertThrows(BusinessRuleException.class, () -> paymentService.refundPayment(1));
    }

    @Test
    void refundPayment_shouldThrowWhenPaymentMissing() {
        when(paymentDao.findByOrderId(42)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.refundPayment(42));
    }
}
