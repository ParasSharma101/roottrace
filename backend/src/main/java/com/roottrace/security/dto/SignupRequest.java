package com.roottrace.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "full name is required")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "valid email required")
        String email,

        @NotBlank(message = "password is required")
        @Size(max = 72, message = "password must be at most 72 UTF-8 bytes")
        String password,

        @NotBlank(message = "workspace name is required")
        String workspaceName
) { @Override public String toString() { return "SignupRequest[redacted]"; } }
