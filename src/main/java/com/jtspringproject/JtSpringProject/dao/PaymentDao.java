package com.jtspringproject.JtSpringProject.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.Payment;

@Repository
public interface PaymentDao extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByOrderId(int orderId);

    Optional<Payment> findByTransactionId(String transactionId);
}
