package com.jtspringproject.JtSpringProject.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.Cart;

@Repository
public interface cartDao extends JpaRepository<Cart, Integer> {

    default Cart addCart(Cart cart) {
        return save(cart);
    }

    default List<Cart> getCarts() {
        return findAll();
    }

    default void updateCart(Cart cart) {
        save(cart);
    }

    default void deleteCart(Cart cart) {
        delete(cart);
    }
}
