package com.roottrace.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteMemberRequest(
        @NotBlank(message = "email is required")
        @Email(message = "valid email required")
        @jakarta.validation.constraints.Size(max=255)
        String email,

        @NotBlank(message = "role is required")
        @jakarta.validation.constraints.Size(max=32)
        String role
) {}
