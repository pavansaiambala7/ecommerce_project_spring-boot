package com.jtspringproject.JtSpringProject.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.Order;

@Repository
public interface OrderDao extends JpaRepository<Order, Integer> {

    /** Associations are LAZY, so API read paths fetch what they serialise. */
    @EntityGraph(attributePaths = { "customer", "items", "items.product" })
    Optional<Order> findWithItemsById(int id);

    @EntityGraph(attributePaths = { "customer", "items", "items.product" })
    List<Order> findByCustomerIdOrderByCreatedAtDesc(int customerId);

    Page<Order> findByCustomerId(int customerId, Pageable pageable);

    List<Order> findByStatus(Order.OrderStatus status);

    boolean existsByIdAndCustomerId(int id, int customerId);
}
