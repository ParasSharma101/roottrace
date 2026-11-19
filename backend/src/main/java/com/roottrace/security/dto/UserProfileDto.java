package com.roottrace.security.dto;

import java.util.List;
import java.util.UUID;

public record UserProfileDto(
        UUID userId,
        String email,
        boolean emailVerified,
        String fullName,
        List<String> roles,
        UUID workspaceId,
        String workspaceName,
        String workspaceSlug,
        String apiKey,
        boolean breakGlass
) {}
