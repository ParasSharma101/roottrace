package com.roottrace.security.dto;

import java.util.UUID;

public record WorkspaceMembershipDto(UUID workspaceId, String name, String slug, String role) {}
