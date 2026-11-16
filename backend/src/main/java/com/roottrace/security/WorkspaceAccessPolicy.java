package com.roottrace.security;

import com.roottrace.domain.model.Workspace;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceAccessPolicy {
    private final boolean development;

    public WorkspaceAccessPolicy(Environment environment) {
        development = environment.acceptsProfiles(Profiles.of("dev & !prod"));
    }

    public boolean canAccess(Workspace workspace) {
        return !workspace.isLegacy() || development;
    }
}
