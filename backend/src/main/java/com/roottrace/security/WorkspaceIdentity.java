package com.roottrace.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

public record WorkspaceIdentity(UUID userId, String email, String fullName, UUID workspaceId, String role)
        implements Principal {
    @Override
    public String getName() { return email; }

    public List<String> roles() {
        if (role == null) return List.of();
        return switch (role) {
            case "ROLE_ADMIN" -> List.of("ROLE_ADMIN", "ROLE_SRE", "ROLE_VIEWER");
            case "ROLE_SRE" -> List.of("ROLE_SRE", "ROLE_VIEWER");
            case "ROLE_VIEWER" -> List.of("ROLE_VIEWER");
            default -> throw new AccessDeniedException("Invalid workspace role");
        };
    }

    public static WorkspaceIdentity require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof WorkspaceIdentity identity)) {
            throw new AccessDeniedException("Account authentication required");
        }
        return identity;
    }

    public UUID requireWorkspace() {
        if (workspaceId == null) throw new AccessDeniedException("Select a workspace first");
        return workspaceId;
    }
}
