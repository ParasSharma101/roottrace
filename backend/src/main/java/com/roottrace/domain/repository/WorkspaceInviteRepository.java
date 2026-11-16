package com.roottrace.domain.repository;

import com.roottrace.domain.model.WorkspaceInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, UUID> {
    Optional<WorkspaceInvite> findByToken(String token);
    List<WorkspaceInvite> findByWorkspaceIdAndStatus(UUID workspaceId, String status);
}
