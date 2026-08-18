package com.jtspringproject.JtSpringProject.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.CartProduct;
import com.jtspringproject.JtSpringProject.models.CartProductId;
import com.jtspringproject.JtSpringProject.models.Product;

@Repository
public interface cartProductDao extends JpaRepository<CartProduct, CartProductId> {

    default CartProduct addCartProduct(CartProduct cartProduct) {
        return save(cartProduct);
    }

    default List<CartProduct> getCartProducts() {
        return findAll();
    }

    @Query("SELECT cp.product FROM CartProduct cp WHERE cp.cart.id = :cartId")
    List<Product> getProductByCartID(@Param("cartId") Integer cartId);

    default void updateCartProduct(CartProduct cartProduct) {
        save(cartProduct);
    }

    default void deleteCartProduct(CartProduct cartProduct) {
        delete(cartProduct);
    }
}
