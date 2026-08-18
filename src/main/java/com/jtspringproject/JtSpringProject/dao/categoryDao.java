package com.jtspringproject.JtSpringProject.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.Category;

@Repository
public interface categoryDao extends JpaRepository<Category, Integer> {

    default Category addCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return save(category);
    }

    default List<Category> getCategories() {
        return findAll();
    }

    default Boolean deleteCategory(int id) {
        if (existsById(id)) {
            deleteById(id);
            return true;
        }
        return false;
    }

    default Category updateCategory(int id, String name) {
        Optional<Category> opt = findById(id);
        if (opt.isEmpty()) {
            return null;
        }
        Category category = opt.get();
        category.setName(name);
        return save(category);
    }

    default Category getCategory(int id) {
        return findById(id).orElse(null);
    }
}
