package com.jtspringproject.JtSpringProject.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.LoginRequest;
import com.jtspringproject.JtSpringProject.dto.request.RefreshRequest;
import com.jtspringproject.JtSpringProject.dto.request.RegisterRequest;
import com.jtspringproject.JtSpringProject.dto.response.TokenResponse;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;
import com.jtspringproject.JtSpringProject.security.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthService authService;

    public AuthApiController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokens = authService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success("Signed in successfully", tokens));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse tokens = authService.register(request.getUsername(), request.getEmail(),
                request.getPassword(), request.getAddress());
        return ResponseEntity.ok(ApiResponse.success("Registered successfully", tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse tokens = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", tokens));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Signed out", null));
    }

    /** Revokes every refresh token for the caller, signing out all their devices. */
    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal AppUserDetails principal) {
        authService.revokeAllForUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Signed out of all sessions", null));
    }
}
