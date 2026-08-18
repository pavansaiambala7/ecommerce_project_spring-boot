package com.jtspringproject.JtSpringProject.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.Order;

@Repository
public interface OrderDao extends JpaRepository<Order, Integer> {

    List<Order> findByCustomerIdOrderByCreatedAtDesc(int customerId);

    Page<Order> findByCustomerId(int customerId, Pageable pageable);

    List<Order> findByStatus(Order.OrderStatus status);
}
