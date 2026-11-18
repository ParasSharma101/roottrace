package com.roottrace.workspace.service;

import com.roottrace.domain.exception.ResourceNotFoundException;
import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.service.SessionRevocationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Mutation lock order: acting account, workspace, then membership/invitation rows. */
@Service
public class MembershipAccess {
    private final JdbcTemplate jdbc;
    private final SessionRevocationService sessions;
    public MembershipAccess(JdbcTemplate jdbc,SessionRevocationService sessions) { this.jdbc=jdbc; this.sessions=sessions; }

    @Transactional(propagation=Propagation.MANDATORY)
    public void lockWorkspace(UUID workspace) {
        var rows=jdbc.queryForList("select id from workspaces where id=? and not is_legacy for no key update",workspace);
        if(rows.isEmpty()) throw new AccessDeniedException("Workspace membership management unavailable");
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Member lockActor(WorkspaceIdentity identity) {
        sessions.lockActive(identity);
        lockWorkspace(identity.requireWorkspace());
        return memberForUser(identity.requireWorkspace(),identity.userId());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Member lockAdmin(WorkspaceIdentity identity,boolean requireVerified) {
        Member actor=lockActor(identity); requireAdmin(actor,requireVerified); return actor;
    }

    public void requireAdmin(Member actor,boolean verified) {
        if(!actor.role().equals("ROLE_ADMIN") || (verified && !actor.verified())) {
            throw new AccessDeniedException(verified?"Verified workspace administrator required":"Workspace administrator required");
        }
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Member memberForUser(UUID workspace,UUID user) {
        var rows=jdbc.query("""
            select m.id,m.user_id,m.role,m.version,u.email,u.full_name,m.created_at,
              (u.email_verified_at is not null and u.verified_email=lower(trim(u.email))) as verified
            from workspace_members m join users u on u.id=m.user_id join workspaces w on w.id=m.workspace_id
            where m.workspace_id=? and m.user_id=? and not w.is_legacy
            """,(rs,n) -> new Member(rs.getObject("id",UUID.class),rs.getObject("user_id",UUID.class),rs.getString("email"),
                rs.getString("full_name"),rs.getString("role"),rs.getLong("version"),rs.getBoolean("verified"),rs.getTimestamp("created_at").toInstant()),workspace,user);
        if(rows.isEmpty()) throw new AccessDeniedException("Workspace membership unavailable");
        return rows.getFirst();
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Member target(UUID workspace,UUID id) {
        var users=jdbc.queryForList("select user_id from workspace_members where id=? and workspace_id=?",id,workspace);
        if(users.isEmpty()) throw new ResourceNotFoundException("Workspace member not found");
        return memberForUser(workspace,(UUID)users.getFirst().get("user_id"));
    }
    public record Member(UUID id,UUID userId,String email,String fullName,String role,long version,boolean verified,java.time.Instant createdAt) {}
}
