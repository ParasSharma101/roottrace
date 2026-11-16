package com.roottrace.security.dto;

import java.util.List;
import java.util.UUID;

public record WorkspaceDetailsDto(
        UUID id,
        String name,
        String slug,
        String apiKey,
        List<TeamMemberDto> members
) {}
