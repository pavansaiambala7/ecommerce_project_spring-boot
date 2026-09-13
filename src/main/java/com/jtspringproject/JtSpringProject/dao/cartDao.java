package com.jtspringproject.JtSpringProject.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.Cart;

@Repository
public interface cartDao extends JpaRepository<Cart, Integer> {

    /** Loads a cart with its lines and their products in one query. */
    @EntityGraph(attributePaths = { "items", "items.product", "items.product.category" })
    Optional<Cart> findByCustomerId(int customerId);

    boolean existsByCustomerId(int customerId);
}
