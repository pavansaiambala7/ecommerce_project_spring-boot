package com.jtspringproject.JtSpringProject.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.Product;

import jakarta.persistence.LockModeType;

@Repository
public interface productDao extends JpaRepository<Product, Integer> {

    /**
     * Category is LAZY, so every read path that renders a category name must
     * fetch it up front. This also removes the N+1 select the product list
     * previously issued.
     */
    @Override
    @EntityGraph(attributePaths = "category")
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Integer id);

    /**
     * Locks the product row for the duration of the transaction so concurrent
     * checkouts cannot both pass the stock check and oversell.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithLockById(Integer id);

    default List<Product> getProducts() {
        return findAll();
    }

    default Product addProduct(Product product) {
        return save(product);
    }

    default Product getProduct(int id) {
        return findById(id).orElse(null);
    }

    default Product updateProduct(Product product) {
        return save(product);
    }

    default boolean deleteProduct(int id) {
        if (existsById(id)) {
            deleteById(id);
            return true;
        }
        return false;
    }
}
