package com.roottrace.security.dto;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberDto(
        UUID userId,
        String fullName,
        String email,
        String role,
        Instant joinedAt
) {}
