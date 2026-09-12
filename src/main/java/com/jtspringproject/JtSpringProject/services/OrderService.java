package com.jtspringproject.JtSpringProject.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.OrderDao;
import com.jtspringproject.JtSpringProject.dao.productDao;
import com.jtspringproject.JtSpringProject.dao.userDao;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
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
        if (items == null || items.isEmpty()) {
            throw new BusinessRuleException("An order must contain at least one item.");
        }

        User user = userDao.getUserById(userId);
        if (user == null) {
            throw ResourceNotFoundException.of("User", userId);
        }

        Order order = new Order();
        order.setCustomer(user);
        order.setStatus(Order.OrderStatus.CREATED);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            if (item.getQuantity() <= 0) {
                throw new BusinessRuleException("Item quantity must be greater than zero.");
            }
            int productId = item.getProduct().getId();

            // PESSIMISTIC_WRITE: holds the row until commit so two concurrent
            // checkouts cannot both observe sufficient stock and oversell.
            Product product = productDao.findWithLockById(productId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));

            if (product.getQuantity() < item.getQuantity()) {
                throw new BusinessRuleException(
                        "Insufficient stock for product: " + product.getName()
                                + " (requested " + item.getQuantity() + ", available " + product.getQuantity() + ")");
            }

            item.setPrice(product.getPrice());
            item.setProduct(product);
            order.addItem(item);
            total = total.add(item.getLineTotal());

            product.setQuantity(product.getQuantity() - item.getQuantity());
            productDao.save(product);
        }

        order.setTotalAmount(total);
        return orderDao.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrderById(int id) {
        return orderDao.findWithItemsById(id).orElse(null);
    }

    /**
     * Reads an order, enforcing that a non-admin caller owns it. Without this the
     * API let any authenticated user read any order by guessing its id.
     */
    @Transactional(readOnly = true)
    public Order getOrderForCaller(int orderId, int callerId, boolean callerIsAdmin) {
        Order order = orderDao.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        assertVisible(order, callerId, callerIsAdmin);
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(int userId) {
        return orderDao.findByCustomerIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Order updateOrderStatus(int orderId, Order.OrderStatus newStatus) {
        Order order = orderDao.findById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        order.setStatus(newStatus);
        return orderDao.save(order);
    }

    @Transactional
    public Order cancelOrder(int orderId, int callerId, boolean callerIsAdmin) {
        Order order = orderDao.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        assertVisible(order, callerId, callerIsAdmin);
        return cancelInternal(order);
    }

    /** Cancellation without an ownership check, for trusted internal callers. */
    @Transactional
    public Order cancelOrder(int orderId) {
        Order order = orderDao.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        return cancelInternal(order);
    }

    private Order cancelInternal(Order order) {
        if (order.getStatus() == Order.OrderStatus.SHIPPED
                || order.getStatus() == Order.OrderStatus.DELIVERED) {
            throw new BusinessRuleException("Cannot cancel an order that is already " + order.getStatus() + ".");
        }
        if (order.getStatus() == Order.OrderStatus.CANCELLED
                || order.getStatus() == Order.OrderStatus.REFUNDED) {
            throw new BusinessRuleException("Order is already " + order.getStatus() + ".");
        }

        restoreStock(order);
        order.setStatus(Order.OrderStatus.CANCELLED);
        return orderDao.save(order);
    }

    /**
     * Returns reserved stock to the catalogue. Locks each row so a concurrent
     * checkout cannot interleave with the restore and lose an update.
     */
    @Transactional
    public void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            int productId = item.getProduct().getId();
            Product product = productDao.findWithLockById(productId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));
            product.setQuantity(product.getQuantity() + item.getQuantity());
            productDao.save(product);
        }
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderDao.findAll();
    }

    private void assertVisible(Order order, int callerId, boolean callerIsAdmin) {
        if (!callerIsAdmin && order.getCustomer().getId() != callerId) {
            throw new AccessDeniedException("You do not have access to this order.");
        }
    }
}
