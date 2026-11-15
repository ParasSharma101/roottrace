package com.roottrace.security.dto;

import java.util.List;

public record AuthResponse(
        String token,
        String email,
        String name,
        List<String> roles
) {}
