package com.jtspringproject.JtSpringProject.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.OrderDao;
import com.jtspringproject.JtSpringProject.dao.productDao;
import com.jtspringproject.JtSpringProject.dao.userDao;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.OrderItem;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.models.User;

@Service
public class OrderService {

    private final OrderDao orderDao;
    private final userDao userDao;
    private final productDao productDao;

    public OrderService(OrderDao orderDao, userDao userDao, productDao productDao) {
        this.orderDao = orderDao;
        this.userDao = userDao;
        this.productDao = productDao;
    }

    @Transactional
    public Order createOrder(int userId, List<OrderItem> items) {
        User user = userDao.getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }

        Order order = new Order();
        order.setCustomer(user);
        order.setStatus(Order.OrderStatus.CREATED);

        double total = 0;
        for (OrderItem item : items) {
            Product product = productDao.getProduct(item.getProduct().getId());
            if (product == null) {
                throw new IllegalArgumentException("Product not found: " + item.getProduct().getId());
            }
            if (product.getQuantity() < item.getQuantity()) {
                throw new IllegalStateException("Insufficient stock for product: " + product.getName());
            }

            item.setPrice(product.getPrice());
            item.setProduct(product);
            order.addItem(item);
            total += (double) product.getPrice() * item.getQuantity();

            // Reduce stock
            product.setQuantity(product.getQuantity() - item.getQuantity());
            productDao.save(product);
        }

        order.setTotalAmount(total);
        return orderDao.save(order);
    }

    public Order getOrderById(int id) {
        return orderDao.findById(id).orElse(null);
    }

    public List<Order> getOrdersByUser(int userId) {
        return orderDao.findByCustomerIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Order updateOrderStatus(int orderId, Order.OrderStatus newStatus) {
        Order order = orderDao.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(newStatus);
        return orderDao.save(order);
    }

    @Transactional
    public Order cancelOrder(int orderId) {
        Order order = orderDao.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() == Order.OrderStatus.SHIPPED || order.getStatus() == Order.OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel order that is already " + order.getStatus());
        }

        // Restore stock
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setQuantity(product.getQuantity() + item.getQuantity());
            productDao.save(product);
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        return orderDao.save(order);
    }

    public List<Order> getAllOrders() {
        return orderDao.findAll();
    }
}
