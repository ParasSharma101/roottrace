package com.roottrace.workspace.service;

import com.roottrace.domain.exception.ResourceNotFoundException;
import com.roottrace.domain.model.WorkspaceMember;
import com.roottrace.domain.repository.WorkspaceMemberRepository;
import com.roottrace.infrastructure.mail.*;
import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.dto.InviteMemberRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Service
public class InvitationService {
    private static final String VIEW="""
        select i.*,w.name as workspace_name,m.status as delivery_status,
          case when i.status='PENDING' and i.expires_at<=current_timestamp then 'EXPIRED' else i.status end as effective_status
        from workspace_invites i join workspaces w on w.id=i.workspace_id
          left join outbound_emails m on m.invitation_id=i.id
        """;
    private static final RowMapper<Invitation> MAPPER=(rs,n) -> new Invitation(rs.getObject("id",UUID.class),rs.getObject("workspace_id",UUID.class),
            rs.getString("workspace_name"),rs.getString("email"),rs.getString("role"),rs.getString("effective_status"),
            time(rs,"created_at"),time(rs,"expires_at"),rs.getString("invited_by_email"),time(rs,"accepted_at"),time(rs,"revoked_at"),
            Objects.toString(rs.getString("delivery_status"),"NOT_QUEUED"));
    private final JdbcTemplate jdbc;
    private final WorkspaceMemberRepository members;
    private final MailSettings mail;
    private final MailOutbox outbox;
    private final int hourlyLimit;
    public InvitationService(JdbcTemplate jdbc,WorkspaceMemberRepository members,MailSettings mail,MailOutbox outbox,
            @Value("${roottrace.invites.hourly-limit:100}") int hourlyLimit) {
        if(hourlyLimit<1) throw new IllegalArgumentException("Invitation hourly limit must be positive");
        this.jdbc=jdbc; this.members=members; this.mail=mail; this.outbox=outbox; this.hourlyLimit=hourlyLimit;
    }

    @Transactional
    public Invitation create(WorkspaceIdentity identity,InviteMemberRequest request) {
        WorkspaceMember admin=admin(identity);
        if(!admin.getUser().isEmailVerified()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Verify your email before inviting teammates");
        mail.requireEnabled();
        String email=request.email().toLowerCase(Locale.ROOT).strip();
        String role=request.role().toUpperCase(Locale.ROOT).strip(); if(!role.startsWith("ROLE_")) role="ROLE_"+role;
        if(!Set.of("ROLE_ADMIN","ROLE_SRE","ROLE_VIEWER").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid invitation role");
        UUID workspace=admin.getWorkspace().getId();
        jdbc.queryForObject("select id from workspaces where id=? for no key update",UUID.class,workspace);
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workspace_members m join users u on u.id=m.user_id where m.workspace_id=? and lower(trim(u.email))=?)",Boolean.class,workspace,email))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This address already belongs to a workspace member");
        }
        jdbc.update("update workspace_invites set status='EXPIRED' where workspace_id=? and email=? and status='PENDING' and expires_at<=current_timestamp",workspace,email);
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workspace_invites where workspace_id=? and email=? and status='PENDING')",Boolean.class,workspace,email))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A pending invitation already exists");
        }
        if(jdbc.queryForObject("select count(*) from workspace_invites where workspace_id=? and created_at>current_timestamp-interval '1 hour'",Long.class,workspace)>=hourlyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Workspace invitation limit reached");
        }
        UUID id=UUID.randomUUID();
        jdbc.update("insert into workspace_invites(id,workspace_id,email,role,invited_by,invited_by_email,expires_at) values (?,?,?,?,?,?,current_timestamp+interval '7 days')",
                id,workspace,email,role,admin.getUser().getId(),admin.getUser().getEmail());
        outbox.enqueue("WORKSPACE_INVITE",email,null,id,"You have been invited to the RootTrace workspace \""+admin.getWorkspace().getName().replaceAll("[\\p{Cntrl}]"," ")+"\" as "+role.replace("ROLE_","")+".\n\nSign in with "+email+", verify that address, and review the invitation:\n"+mail.link("/workspaces")+"\n\nThis invitation expires in seven days. Joining requires your explicit acceptance.");
        return owned(workspace,id);
    }

    @Transactional(readOnly=true)
    public Page list(WorkspaceIdentity identity,UUID before,int limit) {
        UUID workspace=admin(identity).getWorkspace().getId(); validateLimit(limit);
        List<Object> params=new ArrayList<>(List.of(workspace)); String where=" where i.workspace_id=?";
        if(before!=null) { var cursor=owned(workspace,before); where+=" and (i.created_at,i.id)<(?,?)"; params.add(Timestamp.from(cursor.createdAt())); params.add(before); }
        params.add(limit+1);
        var rows=jdbc.query(VIEW+where+" order by i.created_at desc,i.id desc limit ?",MAPPER,params.toArray());
        boolean more=rows.size()>limit; var items=List.copyOf(rows.subList(0,Math.min(rows.size(),limit)));
        return new Page(items,more?items.getLast().id():null);
    }

    @Transactional
    public void revoke(WorkspaceIdentity identity,UUID id) {
        UUID workspace=admin(identity).getWorkspace().getId();
        var rows=jdbc.queryForList("select status from workspace_invites where id=? and workspace_id=? for update",id,workspace);
        if(rows.isEmpty()) throw missing();
        String status=rows.getFirst().get("status").toString();
        if(status.equals("REVOKED")) return;
        if(!status.equals("PENDING") && !status.equals("EXPIRED")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Invitation is already accepted");
        jdbc.update("update workspace_invites set status='REVOKED',revoked_at=current_timestamp,revoked_by=? where id=?",identity.userId(),id);
        jdbc.update("update outbound_emails set status='CANCELLED',encrypted_body=null,nonce=null,claim_token=null,lease_until=null,completed_at=current_timestamp where invitation_id=? and status in ('PENDING','RUNNING','FAILED')",id);
    }

    @Transactional(readOnly=true)
    public Page mine(WorkspaceIdentity identity,UUID before,int limit) {
        String email=verifiedEmail(identity.userId(),false); validateLimit(limit);
        List<Object> params=new ArrayList<>(List.of(email));
        String where=" where i.email=? and i.status='PENDING' and i.expires_at>current_timestamp and not w.is_legacy";
        if(before!=null) {
            var cursor=jdbc.query(VIEW+" where i.id=? and i.email=? and not w.is_legacy",MAPPER,before,email);
            if(cursor.isEmpty()) throw missing();
            where+=" and (i.created_at,i.id)<(?,?)"; params.add(Timestamp.from(cursor.getFirst().createdAt())); params.add(before);
        }
        params.add(limit+1);
        var rows=jdbc.query(VIEW+where+" order by i.created_at desc,i.id desc limit ?",MAPPER,params.toArray());
        boolean more=rows.size()>limit; var items=List.copyOf(rows.subList(0,Math.min(rows.size(),limit)));
        return new Page(items,more?items.getLast().id():null);
    }

    @Transactional
    public Accepted accept(WorkspaceIdentity identity,UUID id) {
        String email=verifiedEmail(identity.userId(),true);
        var rows=jdbc.queryForList("select i.* from workspace_invites i join workspaces w on w.id=i.workspace_id where i.id=? and i.email=? and not w.is_legacy for update of i",id,email);
        if(rows.isEmpty()) throw missing();
        var invite=rows.getFirst(); UUID workspace=(UUID)invite.get("workspace_id");
        if("ACCEPTED".equals(invite.get("status"))) {
            if(!identity.userId().equals(invite.get("accepted_by"))) throw missing();
            return existingMembership(identity.userId(),workspace);
        }
        if(!"PENDING".equals(invite.get("status")) || !Boolean.TRUE.equals(jdbc.queryForObject("select expires_at>clock_timestamp() from workspace_invites where id=?",Boolean.class,id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Invitation is no longer available");
        }
        // No role overwrite if a membership was independently created in the meantime.
        jdbc.update("insert into workspace_members(id,workspace_id,user_id,role) values (?,?,?,?) on conflict (workspace_id,user_id) do nothing",UUID.randomUUID(),workspace,identity.userId(),invite.get("role"));
        jdbc.update("update workspace_invites set status='ACCEPTED',accepted_by=?,accepted_at=current_timestamp where id=?",identity.userId(),id);
        jdbc.update("update outbound_emails set status='CANCELLED',encrypted_body=null,nonce=null,claim_token=null,lease_until=null,completed_at=current_timestamp where invitation_id=? and status in ('PENDING','RUNNING','FAILED')",id);
        return existingMembership(identity.userId(),workspace);
    }
    private Accepted existingMembership(UUID user,UUID workspace) {
        var rows=jdbc.queryForList("select w.id,w.name,m.role from workspace_members m join workspaces w on w.id=m.workspace_id where m.workspace_id=? and m.user_id=?",workspace,user);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"This invitation cannot restore removed membership");
        return new Accepted(workspace,rows.getFirst().get("name").toString(),rows.getFirst().get("role").toString());
    }
    private String verifiedEmail(UUID user,boolean lock) {
        var rows=jdbc.queryForList("select email,verified_email,email_verified_at from users where id=?"+(lock?" for update":""),user);
        if(rows.isEmpty()) throw new AccessDeniedException("Account unavailable");
        var row=rows.getFirst(); String email=row.get("email").toString().toLowerCase(Locale.ROOT).strip();
        if(row.get("email_verified_at")==null || !email.equals(row.get("verified_email"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Verify the invited email before joining");
        return email;
    }
    private WorkspaceMember admin(WorkspaceIdentity identity) {
        return members.findByWorkspaceIdAndUserId(identity.requireWorkspace(),identity.userId())
                .filter(member -> "ROLE_ADMIN".equals(member.getRole()) && !member.getWorkspace().isLegacy())
                .orElseThrow(() -> new AccessDeniedException("Workspace administrator required"));
    }
    private Invitation owned(UUID workspace,UUID id) {
        var rows=jdbc.query(VIEW+" where i.id=? and i.workspace_id=?",MAPPER,id,workspace); if(rows.isEmpty()) throw missing(); return rows.getFirst();
    }
    private ResourceNotFoundException missing() { return new ResourceNotFoundException("Invitation not found"); }
    private void validateLimit(int limit) { if(limit<1 || limit>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Limit must be between 1 and 100"); }
    private static Instant time(ResultSet rs,String field) throws SQLException { var value=rs.getTimestamp(field); return value==null?null:value.toInstant(); }
    public record Invitation(UUID id,UUID workspaceId,String workspaceName,String email,String role,String status,Instant createdAt,Instant expiresAt,
                             String invitedByEmail,Instant acceptedAt,Instant revokedAt,String deliveryStatus) {}
    public record Page(List<Invitation> items,UUID nextCursor) {}
    public record Accepted(UUID workspaceId,String workspaceName,String role) {}
}
