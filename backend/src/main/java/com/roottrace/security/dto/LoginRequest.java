package com.roottrace.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email is required")
        @Email(message = "valid email required")
        String email,

        @NotBlank(message = "password is required")
        String password
) {}
