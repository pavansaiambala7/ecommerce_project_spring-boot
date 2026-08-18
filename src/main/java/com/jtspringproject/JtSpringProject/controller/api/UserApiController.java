package com.jtspringproject.JtSpringProject.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.models.User;
import com.jtspringproject.JtSpringProject.services.userService;

@RestController
@RequestMapping("/api/users")
public class UserApiController {

    private final userService userService;

    public UserApiController(userService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.getUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable int id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> registerUser(@RequestBody User user) {
        if (userService.checkUserExists(user.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Username '" + user.getUsername() + "' is already taken."));
        }
        user.setRole("ROLE_NORMAL");
        User created = userService.addUser(user);
        return ResponseEntity.ok(ApiResponse.success("User registered successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable int id, @RequestBody User user) {
        User updated = userService.updateUserProfile(id, user.getUsername(), user.getEmail(),
                user.getPassword(), user.getAddress());
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", updated));
    }
}
