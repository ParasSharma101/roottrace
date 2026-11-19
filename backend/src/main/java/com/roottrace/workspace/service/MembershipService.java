package com.roottrace.workspace.service;

import com.roottrace.domain.exception.ResourceNotFoundException;
import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.dto.TeamMemberDto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class MembershipService {
    private final JdbcTemplate jdbc;
    private final MembershipAccess access;
    private final MembershipAudit audit;
    public MembershipService(JdbcTemplate jdbc,MembershipAccess access,MembershipAudit audit) { this.jdbc=jdbc; this.access=access; this.audit=audit; }

    @Transactional
    public TeamMemberDto changeRole(WorkspaceIdentity identity,UUID membership,String role,long expectedVersion,String reason) {
        if(!Set.of("ROLE_ADMIN","ROLE_SRE","ROLE_VIEWER").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid membership role");
        reason=reason(reason);
        var actor=access.lockAdmin(identity,true);
        UUID workspace=identity.requireWorkspace(); var target=access.target(workspace,membership);
        version(target,expectedVersion);
        if(target.role().equals(role)) return dto(target);
        protectLastAdmin(workspace,target);
        jdbc.update("update workspace_members set role=?,version=version+1 where id=? and workspace_id=?",role,membership,workspace);
        audit.append(workspace,actor,target,"ROLE_CHANGED",role,reason,0);
        return dto(access.target(workspace,membership));
    }

    @Transactional
    public void remove(WorkspaceIdentity identity,UUID membership,long expectedVersion,String reason) {
        reason=reason(reason);
        var actor=access.lockActor(identity);
        UUID workspace=identity.requireWorkspace(); var target=access.target(workspace,membership);
        boolean self=actor.userId().equals(target.userId());
        if(!self) access.requireAdmin(actor,true);
        version(target,expectedVersion); protectLastAdmin(workspace,target);
        int revoked=jdbc.update("""
            update workspace_invites set status='REVOKED',revoked_by=?,revoked_at=current_timestamp
            where workspace_id=? and email=? and status='PENDING'
            """,actor.userId(),workspace,target.email().toLowerCase(Locale.ROOT).strip());
        jdbc.update("""
            update outbound_emails set status='CANCELLED',encrypted_body=null,nonce=null,claim_token=null,lease_until=null,completed_at=current_timestamp
            where status in ('PENDING','RUNNING','FAILED') and invitation_id in
              (select id from workspace_invites where workspace_id=? and email=? and status='REVOKED')
            """,workspace,target.email().toLowerCase(Locale.ROOT).strip());
        jdbc.update("delete from workspace_members where id=? and workspace_id=?",membership,workspace);
        audit.append(workspace,actor,target,self?"MEMBER_LEFT":"MEMBER_REMOVED",null,reason,revoked);
    }

    @Transactional(readOnly=true)
    public AuditPage history(WorkspaceIdentity identity,UUID before,int limit) {
        access.requireAdmin(access.memberForUser(identity.requireWorkspace(),identity.userId()),false);
        if(limit<1 || limit>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Limit must be between 1 and 100");
        String where=" where workspace_id=?"; List<Object> params=new ArrayList<>(List.of(identity.requireWorkspace()));
        if(before!=null) {
            var cursor=jdbc.queryForList("select created_at from workspace_membership_audit where id=? and workspace_id=?",before,identity.requireWorkspace());
            if(cursor.isEmpty()) throw new ResourceNotFoundException("Membership audit entry not found");
            where+=" and (created_at,id)<(?,?)"; params.add(cursor.getFirst().get("created_at")); params.add(before);
        }
        params.add(limit+1);
        var rows=jdbc.query("select * from workspace_membership_audit"+where+" order by created_at desc,id desc limit ?",(rs,n) -> new AuditEntry(
                rs.getObject("id",UUID.class),rs.getObject("membership_id",UUID.class),rs.getString("actor_email"),rs.getString("target_email"),
                rs.getString("action"),rs.getString("previous_role"),rs.getString("new_role"),rs.getLong("membership_version"),rs.getString("reason"),
                rs.getInt("revoked_invitations"),rs.getTimestamp("created_at").toInstant()),params.toArray());
        boolean more=rows.size()>limit; var items=List.copyOf(rows.subList(0,Math.min(limit,rows.size())));
        return new AuditPage(items,more?items.getLast().id():null);
    }

    private void protectLastAdmin(UUID workspace,MembershipAccess.Member target) {
        if(target.role().equals("ROLE_ADMIN") && jdbc.queryForObject("select count(*) from workspace_members where workspace_id=? and role='ROLE_ADMIN'",Long.class,workspace)<=1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A workspace must retain at least one administrator");
        }
    }
    private void version(MembershipAccess.Member target,long version) {
        if(version<0 || target.version()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"Membership changed; refresh before trying again");
    }
    private String reason(String reason) {
        if(reason==null || reason.isBlank() || reason.length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A reason of at most 500 characters is required");
        return reason.strip();
    }
    private TeamMemberDto dto(MembershipAccess.Member member) {
        return new TeamMemberDto(member.userId(),member.fullName(),member.email(),member.role(),member.createdAt(),member.id(),member.version());
    }
    public record AuditEntry(UUID id,UUID membershipId,String actorEmail,String targetEmail,String action,String previousRole,String newRole,
                             long membershipVersion,String reason,int revokedInvitations,Instant createdAt) {}
    public record AuditPage(List<AuditEntry> items,UUID nextCursor) {}
}
