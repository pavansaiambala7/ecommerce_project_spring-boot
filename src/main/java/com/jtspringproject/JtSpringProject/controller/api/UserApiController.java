package com.jtspringproject.JtSpringProject.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.UserUpdateRequest;
import com.jtspringproject.JtSpringProject.dto.response.UserResponse;
import com.jtspringproject.JtSpringProject.models.User;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;
import com.jtspringproject.JtSpringProject.security.AuthService;
import com.jtspringproject.JtSpringProject.services.userService;

import jakarta.validation.Valid;

/**
 * User directory and profile management.
 *
 * <p>Registration lives on {@code /api/auth/register}. Every method here now
 * returns {@link UserResponse}; returning the entity published each account's
 * BCrypt password hash to anonymous callers.
 */
@RestController
@RequestMapping("/api/users")
public class UserApiController {

    private final userService userService;
    private final AuthService authService;

    public UserApiController(userService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userService.getUsers().stream().map(UserResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /** Returns the authenticated caller's own profile. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal AppUserDetails principal) {
        User user = userService.requireUserById(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable int id) {
        User user = userService.requireUserById(id);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    /**
     * Updates a profile. A caller may only edit their own account unless they are
     * an administrator; this endpoint previously accepted any id from anyone and
     * would set that account's password, which was a complete account takeover.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@PathVariable int id,
            @Valid @RequestBody UserUpdateRequest request) {
        User updated = userService.updateUserProfile(id, request.getUsername(), request.getEmail(),
                request.getPassword(), request.getAddress());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            // Credentials changed: existing refresh tokens must stop working.
            authService.revokeAllForUser(id);
        }

        return ResponseEntity.ok(ApiResponse.success("User updated successfully", UserResponse.from(updated)));
    }
}
