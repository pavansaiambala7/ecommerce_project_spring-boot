package com.jtspringproject.JtSpringProject.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.User;

@Repository
public interface userDao extends JpaRepository<User, Integer> {

    User findByUsername(String username);

    boolean existsByUsername(String username);

    default List<User> getAllUser() {
        return findAll();
    }

    default User saveUser(User user) {
        return save(user);
    }

    default boolean userExists(String username) {
        return existsByUsername(username);
    }

    default User getUserByUsername(String username) {
        return findByUsername(username);
    }

    default User getUserById(int id) {
        return findById(id).orElse(null);
    }
}