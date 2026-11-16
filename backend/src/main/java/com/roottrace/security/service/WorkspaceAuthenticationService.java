package com.roottrace.security.service;

import com.roottrace.domain.repository.UserRepository;
import com.roottrace.domain.repository.WorkspaceMemberRepository;
import com.roottrace.security.WorkspaceIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class WorkspaceAuthenticationService {
    private final UserRepository users;
    private final WorkspaceMemberRepository memberships;

    public WorkspaceAuthenticationService(UserRepository users, WorkspaceMemberRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceIdentity> authenticate(UUID userId, UUID workspaceId) {
        return users.findById(userId).flatMap(user -> {
            if (workspaceId == null) return Optional.of(new WorkspaceIdentity(
                    userId, user.getEmail(), user.getFullName(), null, null));
            return memberships.findByWorkspaceIdAndUserId(workspaceId, userId)
                    .map(member -> new WorkspaceIdentity(userId, user.getEmail(), user.getFullName(),
                            workspaceId, member.getRole()));
        });
    }
}
