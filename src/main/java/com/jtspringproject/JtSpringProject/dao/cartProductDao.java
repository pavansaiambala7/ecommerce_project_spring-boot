package com.jtspringproject.JtSpringProject.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.CartProduct;
import com.jtspringproject.JtSpringProject.models.CartProductId;
import com.jtspringproject.JtSpringProject.models.Product;

@Repository
public interface cartProductDao extends JpaRepository<CartProduct, CartProductId> {

    Optional<CartProduct> findByCartIdAndProductId(int cartId, int productId);

    List<CartProduct> findByCartId(int cartId);

    void deleteByCartId(int cartId);

    @Query("SELECT cp.product FROM CartProduct cp WHERE cp.cart.id = :cartId")
    List<Product> getProductByCartID(@Param("cartId") Integer cartId);
}
