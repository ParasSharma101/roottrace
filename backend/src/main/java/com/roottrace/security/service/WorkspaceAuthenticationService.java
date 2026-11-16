package com.roottrace.security.service;

import com.roottrace.domain.repository.UserRepository;
import com.roottrace.domain.repository.WorkspaceMemberRepository;
import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.WorkspaceAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class WorkspaceAuthenticationService {
    private final UserRepository users;
    private final WorkspaceMemberRepository memberships;
    private final WorkspaceAccessPolicy accessPolicy;

    public WorkspaceAuthenticationService(UserRepository users, WorkspaceMemberRepository memberships, WorkspaceAccessPolicy accessPolicy) {
        this.users = users;
        this.memberships = memberships;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceIdentity> authenticate(UUID userId, UUID workspaceId) {
        return users.findById(userId).flatMap(user -> {
            if (workspaceId == null) return Optional.of(new WorkspaceIdentity(
                    userId, user.getEmail(), user.getFullName(), null, null));
            return memberships.findByWorkspaceIdAndUserId(workspaceId, userId)
                    .filter(member -> accessPolicy.canAccess(member.getWorkspace()))
                    .map(member -> new WorkspaceIdentity(userId, user.getEmail(), user.getFullName(),
                            workspaceId, member.getRole()));
        });
    }
}
