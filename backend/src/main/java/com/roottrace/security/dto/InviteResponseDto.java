package com.roottrace.security.dto;

import java.time.Instant;
import java.util.UUID;

public record InviteResponseDto(
        UUID id,
        String email,
        String role,
        String status,
        String token,
        Instant expiresAt
) {}
