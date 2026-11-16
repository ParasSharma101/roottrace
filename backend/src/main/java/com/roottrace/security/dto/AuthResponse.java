package com.roottrace.security.dto;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String email,
        String fullName,
        List<String> roles,
        UUID workspaceId,
        String workspaceName,
        String workspaceSlug,
        String apiKey
) {}
