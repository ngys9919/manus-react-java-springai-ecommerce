package com.shopwise.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "full name is required")
            @Size(max = 120, message = "full name is too long")
            String fullName,

            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid address")
            String email,

            @NotBlank(message = "password is required")
            @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid address")
            String email,

            @NotBlank(message = "password is required")
            String password) {
    }

    public record AuthResponse(
            String token,
            String tokenType,
            long expiresInSeconds,
            UserView user) {
    }

    public record UserView(
            Long id,
            String email,
            String fullName,
            String role,
            boolean staff) {
    }
}