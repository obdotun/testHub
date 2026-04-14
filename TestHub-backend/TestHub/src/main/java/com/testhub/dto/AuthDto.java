package com.testhub.dto;

import com.testhub.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

public class AuthDto {

    // ── Login ─────────────────────────────────────────────────────────────

    @Data
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class LoginResponse {
        private String        token;
        private String        username;
        private String        fullName;
        private String        email;
        private Role          role;
        private boolean       firstLogin; // → forcer changement password
    }

    // ── Change password ───────────────────────────────────────────────────

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        private String newPassword;
    }

    // ── Gestion utilisateurs (ADMIN) ──────────────────────────────────────

    @Data
    public static class CreateUserRequest {
        @NotBlank
        private String username;

        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String fullName;

        @NotNull
        private Role role;

        @NotBlank
        @Size(min = 8)
        private String password;
    }

    @Data
    public static class UpdateUserRequest {
        private String  fullName;
        private String  email;
        private Role    role;
        private Boolean active;
    }

    @Data
    public static class UserResponse {
        private Long          id;
        private String        username;
        private String        email;
        private String        fullName;
        private Role          role;
        private boolean       firstLogin;
        private boolean       active;
        private LocalDateTime createdAt;
        private LocalDateTime lastLoginAt;
    }
}