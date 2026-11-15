package com.roottrace.security.dto;

import jakarta.validation.constraints.NotBlank;

public record DemoLoginRequest(
        @NotBlank(message = "role is required (SRE, VIEWER, ADMIN)")
        String role
) {}
