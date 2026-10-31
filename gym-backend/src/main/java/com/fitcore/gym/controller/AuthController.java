package com.fitcore.gym.controller;

import com.fitcore.gym.dto.auth.AuthRequest;
import com.fitcore.gym.dto.auth.AuthResponse;
import com.fitcore.gym.dto.auth.ForgotPasswordRequest;
import com.fitcore.gym.dto.auth.RegisterRequest;
import com.fitcore.gym.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final AuthService authService;

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public com.fitcore.gym.dto.user.SessionUserDto me() {
        return authService.me();
    }

    @Operation(
            summary = "Register a new user",
            description = "Invitation registration will be enabled with owner onboarding.",
            responses = {
                @ApiResponse(responseCode = "200", description = "User successfully registered"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error or invalid request"),
                @ApiResponse(responseCode = "409", description = "Email already in use")
            })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(
            summary = "Authenticate a user",
            description = "Authenticates a user by email and password, returning a JWT token.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
                @ApiResponse(responseCode = "401", description = "Invalid credentials"),
                @ApiResponse(responseCode = "400", description = "Validation error")
            })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "Request password reset",
            description = "Accepts a password reset request without exposing account existence.",
            responses = {
                @ApiResponse(responseCode = "202", description = "Reset request accepted"),
                @ApiResponse(responseCode = "400", description = "Validation error")
            })
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
