package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.jtspringproject.JtSpringProject.dao.OrderDao;
import com.jtspringproject.JtSpringProject.dao.productDao;
import com.jtspringproject.JtSpringProject.dao.userDao;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.OrderItem;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.models.User;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final int OWNER_ID = 1;
    private static final int OTHER_USER_ID = 42;

    @Mock
    private OrderDao orderDao;

    @Mock
    private userDao userDao;

    @Mock
    private productDao productDao;

    @InjectMocks
    private OrderService orderService;

    private User testUser;
    private Product testProduct;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(OWNER_ID);
        testUser.setUsername("testuser");

        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Apple");
        testProduct.setPrice(new BigDecimal("3.00"));
        testProduct.setQuantity(40);

        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setCustomer(testUser);
        testOrder.setStatus(Order.OrderStatus.CREATED);
        testOrder.setTotalAmount(new BigDecimal("9.00"));
    }

    private OrderItem itemFor(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void createOrder_shouldCreateOrderWithValidData() {
        when(userDao.getUserById(OWNER_ID)).thenReturn(testUser);
        when(productDao.findWithLockById(1)).thenReturn(Optional.of(testProduct));
        when(orderDao.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productDao.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.createOrder(OWNER_ID, List.of(itemFor(testProduct, 3)));

        assertNotNull(result);
        assertEquals(Order.OrderStatus.CREATED, result.getStatus());
        assertEquals(0, new BigDecimal("9.00").compareTo(result.getTotalAmount()));
        assertEquals(37, testProduct.getQuantity());
    }

    /**
     * Money is decimal. With double arithmetic, 3 x 0.10 does not equal 0.30.
     */
    @Test
    void createOrder_shouldTotalExactlyInDecimal() {
        testProduct.setPrice(new BigDecimal("0.10"));
        when(userDao.getUserById(OWNER_ID)).thenReturn(testUser);
        when(productDao.findWithLockById(1)).thenReturn(Optional.of(testProduct));
        when(orderDao.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productDao.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.createOrder(OWNER_ID, List.of(itemFor(testProduct, 3)));

        assertEquals(0, new BigDecimal("0.30").compareTo(result.getTotalAmount()));
    }

    @Test
    void createOrder_shouldThrowWhenUserNotFound() {
        when(userDao.getUserById(999)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () ->
                orderService.createOrder(999, List.of(itemFor(testProduct, 1))));
    }

    @Test
    void createOrder_shouldRejectEmptyItemList() {
        assertThrows(BusinessRuleException.class, () -> orderService.createOrder(OWNER_ID, List.of()));
    }

    @Test
    void createOrder_shouldThrowWhenInsufficientStock() {
        testProduct.setQuantity(2);
        when(userDao.getUserById(OWNER_ID)).thenReturn(testUser);
        when(productDao.findWithLockById(1)).thenReturn(Optional.of(testProduct));

        assertThrows(BusinessRuleException.class, () ->
                orderService.createOrder(OWNER_ID, List.of(itemFor(testProduct, 5))));

        verify(orderDao, never()).save(any(Order.class));
    }

    /**
     * Stock must be read under a row lock. A plain read lets two concurrent
     * checkouts both observe sufficient stock and oversell.
     */
    @Test
    void createOrder_shouldReadStockUnderLock() {
        when(userDao.getUserById(OWNER_ID)).thenReturn(testUser);
        when(productDao.findWithLockById(1)).thenReturn(Optional.of(testProduct));
        when(orderDao.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productDao.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        orderService.createOrder(OWNER_ID, List.of(itemFor(testProduct, 1)));

        verify(productDao).findWithLockById(1);
        verify(productDao, never()).getProduct(1);
    }

    @Test
    void getOrderById_shouldReturnOrderWhenExists() {
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));

        Order result = orderService.getOrderById(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void getOrdersByUser_shouldReturnUserOrders() {
        when(orderDao.findByCustomerIdOrderByCreatedAtDesc(OWNER_ID)).thenReturn(List.of(testOrder));

        assertEquals(1, orderService.getOrdersByUser(OWNER_ID).size());
    }

    /**
     * Regression: any authenticated caller could read any order by guessing its id.
     */
    @Test
    void getOrderForCaller_shouldRejectSomeoneElsesOrder() {
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));

        assertThrows(AccessDeniedException.class, () ->
                orderService.getOrderForCaller(1, OTHER_USER_ID, false));
    }

    @Test
    void getOrderForCaller_shouldAllowAdmin() {
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));

        assertNotNull(orderService.getOrderForCaller(1, OTHER_USER_ID, true));
    }

    @Test
    void updateOrderStatus_shouldUpdateStatus() {
        when(orderDao.findById(1)).thenReturn(Optional.of(testOrder));
        when(orderDao.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.updateOrderStatus(1, Order.OrderStatus.PAID);

        assertEquals(Order.OrderStatus.PAID, result.getStatus());
    }

    @Test
    void cancelOrder_shouldCancelAndRestoreStock() {
        testOrder.addItem(itemFor(testProduct, 3));
        testProduct.setQuantity(37);

        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));
        when(orderDao.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productDao.findWithLockById(1)).thenReturn(Optional.of(testProduct));
        when(productDao.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.cancelOrder(1);

        assertEquals(Order.OrderStatus.CANCELLED, result.getStatus());
        assertEquals(40, testProduct.getQuantity());
    }

    @Test
    void cancelOrder_shouldThrowWhenAlreadyShipped() {
        testOrder.setStatus(Order.OrderStatus.SHIPPED);
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));

        assertThrows(BusinessRuleException.class, () -> orderService.cancelOrder(1));
    }

    @Test
    void cancelOrder_shouldRejectSomeoneElsesOrder() {
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));

        assertThrows(AccessDeniedException.class, () ->
                orderService.cancelOrder(1, OTHER_USER_ID, false));

        verify(orderDao, never()).save(any(Order.class));
    }

    @Test
    void cancelOrder_shouldThrowWhenAlreadyCancelled() {
        testOrder.setStatus(Order.OrderStatus.CANCELLED);
        when(orderDao.findWithItemsById(1)).thenReturn(Optional.of(testOrder));

        assertThrows(BusinessRuleException.class, () -> orderService.cancelOrder(1));
    }
}
