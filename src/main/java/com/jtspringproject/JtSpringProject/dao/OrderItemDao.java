package com.jtspringproject.JtSpringProject.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.OrderItem;

import java.util.List;

@Repository
public interface OrderItemDao extends JpaRepository<OrderItem, Integer> {

    List<OrderItem> findByOrderId(int orderId);
}
