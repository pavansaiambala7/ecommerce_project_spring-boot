package com.jtspringproject.JtSpringProject.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.Product;

@Repository
public interface productDao extends JpaRepository<Product, Integer> {

    List<Product> findAll();

    Optional<Product> findById(Integer id);

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
