package com.roottrace.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

public record IngestionIdentity(UUID workspaceId) implements Principal {
    public IngestionIdentity { Objects.requireNonNull(workspaceId); }
    @Override public String getName() { return "ingestion-service"; }

    public static UUID requireWorkspace(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            if (authentication.getPrincipal() instanceof IngestionIdentity identity) return identity.workspaceId();
            if (authentication.getPrincipal() instanceof WorkspaceIdentity identity) return identity.requireWorkspace();
        }
        throw new AccessDeniedException("Workspace ingestion identity required");
    }
}
