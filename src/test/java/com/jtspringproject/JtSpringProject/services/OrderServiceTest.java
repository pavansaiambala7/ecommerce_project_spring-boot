package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jtspringproject.JtSpringProject.dao.OrderDao;
import com.jtspringproject.JtSpringProject.dao.productDao;
import com.jtspringproject.JtSpringProject.dao.userDao;
import com.jtspringproject.JtSpringProject.models.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

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
        testUser.setId(1);
        testUser.setUsername("testuser");

        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Apple");
        testProduct.setPrice(3);
        testProduct.setQuantity(40);

        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setCustomer(testUser);
        testOrder.setStatus(Order.OrderStatus.CREATED);
        testOrder.setTotalAmount(9.0);
    }

    @Test
    void createOrder_shouldCreateOrderWithValidData() {
        when(userDao.getUserById(1)).thenReturn(testUser);
        when(productDao.getProduct(1)).thenReturn(testProduct);
        when(orderDao.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productDao.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        OrderItem item = new OrderItem();
        item.setProduct(testProduct);
        item.setQuantity(3);

        Order result = orderService.createOrder(1, List.of(item));

        assertNotNull(result);
        assertEquals(Order.OrderStatus.CREATED, result.getStatus());
        assertEquals(9.0, result.getTotalAmount());
        assertEquals(37, testProduct.getQuantity()); // Stock reduced
    }

    @Test
    void createOrder_shouldThrowWhenUserNotFound() {
        when(userDao.getUserById(999)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(999, List.of()));
    }

    @Test
    void createOrder_shouldThrowWhenInsufficientStock() {
        when(userDao.getUserById(1)).thenReturn(testUser);
        testProduct.setQuantity(2);
        when(productDao.getProduct(1)).thenReturn(testProduct);

        OrderItem item = new OrderItem();
        item.setProduct(testProduct);
        item.setQuantity(5);

        assertThrows(IllegalStateException.class, () ->
                orderService.createOrder(1, List.of(item)));
    }

    @Test
    void getOrderById_shouldReturnOrderWhenExists() {
        when(orderDao.findById(1)).thenReturn(Optional.of(testOrder));

        Order result = orderService.getOrderById(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void getOrdersByUser_shouldReturnUserOrders() {
        when(orderDao.findByCustomerIdOrderByCreatedAtDesc(1)).thenReturn(List.of(testOrder));

        List<Order> result = orderService.getOrdersByUser(1);

        assertEquals(1, result.size());
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
        OrderItem item = new OrderItem();
        item.setProduct(testProduct);
        item.setQuantity(3);
        testOrder.addItem(item);
        testProduct.setQuantity(37);

        when(orderDao.findById(1)).thenReturn(Optional.of(testOrder));
        when(orderDao.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productDao.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.cancelOrder(1);

        assertEquals(Order.OrderStatus.CANCELLED, result.getStatus());
        assertEquals(40, testProduct.getQuantity()); // Stock restored
    }

    @Test
    void cancelOrder_shouldThrowWhenAlreadyShipped() {
        testOrder.setStatus(Order.OrderStatus.SHIPPED);
        when(orderDao.findById(1)).thenReturn(Optional.of(testOrder));

        assertThrows(IllegalStateException.class, () ->
                orderService.cancelOrder(1));
    }
}
