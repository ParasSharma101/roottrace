package com.roottrace.security.dto;

import java.util.List;

public record UserProfileDto(
        String email,
        String name,
        List<String> roles,
        boolean breakGlass
) {}
