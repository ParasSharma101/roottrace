package com.roottrace.security.service;

import com.roottrace.domain.model.WorkspaceMember;
import com.roottrace.domain.repository.WorkspaceMemberRepository;
import com.roottrace.security.WorkspaceIdentity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class WorkspaceOperatorAccess {
    private final WorkspaceMemberRepository memberships;
    public WorkspaceOperatorAccess(WorkspaceMemberRepository memberships) { this.memberships=memberships; }
    @Transactional(propagation=Propagation.MANDATORY)
    public WorkspaceMember require(WorkspaceIdentity identity) {
        return memberships.findByWorkspaceIdAndUserId(identity.requireWorkspace(),identity.userId())
                .filter(member -> Set.of("ROLE_ADMIN","ROLE_SRE").contains(member.getRole()) && !member.getWorkspace().isLegacy())
                .orElseThrow(() -> new AccessDeniedException("Workspace operator required"));
    }
}
