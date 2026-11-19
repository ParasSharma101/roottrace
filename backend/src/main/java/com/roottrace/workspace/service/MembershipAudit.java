package com.roottrace.workspace.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

@Service
public class MembershipAudit {
    private final JdbcTemplate jdbc;
    public MembershipAudit(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void append(UUID workspace,MembershipAccess.Member actor,MembershipAccess.Member target,String action,String newRole,String reason,int revoked) {
        jdbc.update("""
            insert into workspace_membership_audit(id,workspace_id,membership_id,actor_user_id,actor_email,target_user_id,target_email,
                action,previous_role,new_role,membership_version,reason,revoked_invitations) values (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),workspace,target.id(),actor.userId(),actor.email(),target.userId(),target.email(),action,target.role(),newRole,target.version(),reason,revoked);
    }
}
