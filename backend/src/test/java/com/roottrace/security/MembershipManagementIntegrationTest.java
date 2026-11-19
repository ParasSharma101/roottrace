package com.roottrace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import com.roottrace.support.IntegrationTestSupport;
import com.roottrace.workspace.service.MembershipAudit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class MembershipManagementIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;
    @Autowired JwtTokenProvider jwt;
    @SpyBean MembershipAudit audit;
    @Autowired javax.sql.DataSource dataSource;
    @org.springframework.boot.test.web.server.LocalServerPort int port;

    @Test
    void roleChangeUpdatesLivePermissionsAndAuditWithOptimisticVersion() throws Exception {
        Account admin=account(),target=account(); UUID membership=join(target,admin.workspace(),"ROLE_VIEWER");
        String selected=selected(target,admin.workspace());
        mvc.perform(get("/api/v1/workspace/members/audit").header("Authorization",selected)).andExpect(status().isForbidden());
        assertThat(role(admin.token(),membership,"ROLE_ADMIN",0)).isEqualTo(200);
        mvc.perform(get("/api/v1/workspace/members/audit").header("Authorization",selected)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actorEmail").value(admin.email())).andExpect(jsonPath("$.items[0].targetEmail").value(target.email()))
                .andExpect(jsonPath("$.items[0].previousRole").value("ROLE_VIEWER")).andExpect(jsonPath("$.items[0].newRole").value("ROLE_ADMIN"));
        assertThat(role(admin.token(),membership,"ROLE_SRE",0)).isEqualTo(409);
        assertThat(role(admin.token(),membership,"ROLE_ADMIN",1)).isEqualTo(200); // No-op does not create history/version churn.
        assertThat(auditCount(admin.workspace())).isEqualTo(1);
        assertThat(role(admin.token(),membership,"ROLE_VIEWER",1)).isEqualTo(200);
        mvc.perform(get("/api/v1/workspace/members/audit").header("Authorization",selected)).andExpect(status().isForbidden());
        assertThat(version(membership)).isEqualTo(2);
    }

    @Test
    void lastAdministratorCannotDemoteRemoveOrLeaveAndHandoffWorks() throws Exception {
        Account admin=account();
        assertThat(role(admin.token(),admin.membership(),"ROLE_VIEWER",0)).isEqualTo(409);
        assertThat(remove(admin.token(),admin.membership(),0)).isEqualTo(409);
        Account next=account(); UUID nextMembership=join(next,admin.workspace(),"ROLE_VIEWER");
        assertThat(role(admin.token(),nextMembership,"ROLE_ADMIN",0)).isEqualTo(200);
        assertThat(remove(admin.token(),admin.membership(),0)).isEqualTo(204);
        assertThat(adminCount(admin.workspace())).isEqualTo(1);
        mvc.perform(get("/api/v1/workspace/members/audit").header("Authorization",selected(next,admin.workspace())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action").value("MEMBER_LEFT"));
    }

    @Test
    void twoConcurrentAdministratorDemotionsRetainOneAdministrator() throws Exception {
        Account one=account(),two=account(); UUID second=join(two,one.workspace(),"ROLE_ADMIN");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> role(one.token(),one.membership(),"ROLE_SRE",0));
            var b=pool.submit(() -> role(selected(two,one.workspace()),second,"ROLE_SRE",0));
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(adminCount(one.workspace())).isEqualTo(1); assertThat(auditCount(one.workspace())).isEqualTo(1);
    }

    @Test
    void twoConcurrentAdministratorDeparturesRetainOneAdministrator() throws Exception {
        Account one=account(),two=account(); UUID second=join(two,one.workspace(),"ROLE_ADMIN");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> remove(one.token(),one.membership(),0));
            var b=pool.submit(() -> remove(selected(two,one.workspace()),second,0));
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);
        }
        assertThat(adminCount(one.workspace())).isEqualTo(1); assertThat(auditCount(one.workspace())).isEqualTo(1);
    }

    @Test
    void removalInvalidatesOnlyWorkspaceAccessAndOldMembershipCannotAffectRejoin() throws Exception {
        Account admin=account(),target=account(); UUID membership=join(target,admin.workspace(),"ROLE_SRE");
        UUID invitation=pending(admin,target);
        String selected=selected(target,admin.workspace()),own=target.token();
        assertThat(remove(admin.token(),membership,1)).isEqualTo(409);
        assertThat(remove(admin.token(),membership,0)).isEqualTo(204);
        assertThat(jdbc.queryForObject("select status from workspace_invites where id=?",String.class,invitation)).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("select encrypted_body from outbound_emails where invitation_id=?",byte[].class,invitation)).isNull();
        assertThat(jdbc.queryForObject("select revoked_invitations from workspace_membership_audit where workspace_id=?",Integer.class,admin.workspace())).isEqualTo(1);
        mvc.perform(get("/api/v1/workspace").header("Authorization",selected)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/workspace").header("Authorization",own)).andExpect(status().isOk());
        String account="Bearer "+jwt.generateToken(target.user(),null);
        mvc.perform(post("/api/v1/auth/select-workspace").header("Authorization",account).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("workspaceId",admin.workspace())))).andExpect(status().isNotFound());
        UUID rejoined=join(target,admin.workspace(),"ROLE_VIEWER");
        assertThat(remove(admin.token(),membership,0)).isEqualTo(404);
        assertThat(version(rejoined)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from users where id=?",Integer.class,target.user())).isEqualTo(1);
    }

    @Test
    void viewersCanLeaveButCannotManageOthersAndUnverifiedAdminsCannotChangeRoles() throws Exception {
        Account admin=account(),target=account(); UUID membership=join(target,admin.workspace(),"ROLE_VIEWER");
        String viewer=selected(target,admin.workspace());
        assertThat(role(viewer,admin.membership(),"ROLE_VIEWER",0)).isEqualTo(403);
        assertThat(remove(viewer,admin.membership(),0)).isEqualTo(403);
        jdbc.update("update users set verified_email=null,email_verified_at=null where id=?",admin.user());
        assertThat(role(admin.token(),membership,"ROLE_ADMIN",0)).isEqualTo(403);
        assertThat(remove(admin.token(),membership,0)).isEqualTo(403);
        jdbc.update("update users set verified_email=null,email_verified_at=null where id=?",target.user());
        assertThat(remove(viewer,membership,0)).isEqualTo(204);
    }

    @Test
    void foreignTargetsAndAuditCursorsAreHiddenAndInputIsValidated() throws Exception {
        Account admin=account(),foreign=account(),target=account(); UUID membership=join(target,admin.workspace(),"ROLE_VIEWER");
        assertThat(role(admin.token(),foreign.membership(),"ROLE_VIEWER",0)).isEqualTo(404);
        assertThat(remove(admin.token(),foreign.membership(),0)).isEqualTo(404);
        assertThat(role(admin.token(),membership,"ROLE_ROOT",0)).isEqualTo(400);
        mvc.perform(patch("/api/v1/workspace/members/"+membership+"/role").header("Authorization",admin.token()).contentType("application/json")
                .content("{\"role\":\"ROLE_ADMIN\",\"reason\":\"Promote\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/workspace/members/"+membership+"/remove").header("Authorization",admin.token()).contentType("application/json")
                .content("{\"expectedVersion\":0,\"reason\":\" \"}")).andExpect(status().isBadRequest());
        role(admin.token(),membership,"ROLE_ADMIN",0);
        UUID cursor=jdbc.queryForObject("select id from workspace_membership_audit where workspace_id=?",UUID.class,admin.workspace());
        mvc.perform(get("/api/v1/workspace/members/audit?before="+cursor).header("Authorization",foreign.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/workspace/members/audit?limit=0").header("Authorization",admin.token())).andExpect(status().isBadRequest());
        assertThat(auditCount(foreign.workspace())).isZero();
    }

    @Test
    void removalRevokesPendingInvitationsAndRacingAcceptanceCannotRestoreMembership() throws Exception {
        Account admin=account(),target=account(); UUID membership=join(target,admin.workspace(),"ROLE_VIEWER");
        UUID invite=UUID.randomUUID();
        jdbc.update("insert into workspace_invites(id,workspace_id,email,role,invited_by,invited_by_email,expires_at) values (?,?,?,'ROLE_ADMIN',?,?,now()+interval '1 day')",
                invite,admin.workspace(),target.email(),admin.user(),admin.email());
        jdbc.update("insert into outbound_emails(id,purpose,recipient,invitation_id,encrypted_body,nonce) values (?,'WORKSPACE_INVITE',?,?,?,?)",
                UUID.randomUUID(),target.email(),invite,new byte[]{1,2,3},new byte[12]);
        String account="Bearer "+jwt.generateToken(target.user(),null);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> remove(admin.token(),membership,0));
            var b=pool.submit(() -> mvc.perform(post("/api/v1/auth/invitations/"+invite+"/accept").header("Authorization",account)).andReturn().getResponse().getStatus());
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(204); assertThat(b.get(15,TimeUnit.SECONDS)).isIn(200,409);
        }
        assertThat(jdbc.queryForObject("select count(*) from workspace_members where workspace_id=? and user_id=?",Integer.class,admin.workspace(),target.user())).isZero();
        mvc.perform(post("/api/v1/auth/invitations/"+invite+"/accept").header("Authorization",account)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select encrypted_body from outbound_emails where invitation_id=?",byte[].class,invite)).isNull();
    }

    @Test
    void failedAuditRollsBackMembershipAndInvitationChanges() throws Exception {
        Account admin=account(),target=account(); UUID membership=join(target,admin.workspace(),"ROLE_VIEWER");
        UUID invitation=pending(admin,target);
        MembershipAudit underlying=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("audit unavailable")).when(underlying)
                .append(any(),any(),any(),anyString(),nullable(String.class),anyString(),anyInt());
        assertThat(role(admin.token(),membership,"ROLE_ADMIN",0)).isEqualTo(503);
        assertThat(version(membership)).isZero();
        assertThat(remove(admin.token(),membership,0)).isEqualTo(503);
        assertThat(jdbc.queryForObject("select role from workspace_members where id=?",String.class,membership)).isEqualTo("ROLE_VIEWER");
        assertThat(auditCount(admin.workspace())).isZero();
        assertThat(jdbc.queryForObject("select status from workspace_invites where id=?",String.class,invitation)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select encrypted_body from outbound_emails where invitation_id=?",byte[].class,invitation)).isNotNull();
    }

    @Test
    void auditForeignKeyDoesNotDeadlockWithTargetsSessionLock() throws Exception {
        Account admin=account(),target=account(); UUID membership=join(target,admin.workspace(),"ROLE_VIEWER");
        try(var pool=Executors.newSingleThreadExecutor(); var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("select id from users where id=? for no key update")) {
                statement.setObject(1,target.user()); statement.executeQuery().close();
            }
            var removed=pool.submit(() -> remove(admin.token(),membership,0));
            assertThat(removed.get(3,TimeUnit.SECONDS)).isEqualTo(204);
            connection.rollback();
        }
    }

    @Test
    void realHttpMemberChangeAndRemovalUseLiveAuthorization() throws Exception {
        Account admin=account(),target=account(); UUID membership=join(target,admin.workspace(),"ROLE_VIEWER");
        try(var client=HttpClient.newHttpClient()) {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/workspace/members/"+membership+"/role"))
                    .timeout(java.time.Duration.ofSeconds(10)).header("Authorization",admin.token()).header("Content-Type","application/json")
                    .method("PATCH",HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("role","ROLE_SRE","expectedVersion",0,"reason","On-call rotation")))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200); assertThat(mapper.readTree(response.body()).path("version").asInt()).isEqualTo(1);
            assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertThat(remove(admin.token(),membership,1)).isEqualTo(204);
            var check=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/workspace")).header("Authorization",selected(target,admin.workspace())).build();
            assertThat(client.send(check,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        }
    }

    private Account account() {
        String unique=UUID.randomUUID().toString();
        User user=users.saveAndFlush(new User(unique+"@example.com","unused-password-hash","Member test"));
        Workspace workspace=workspaces.saveAndFlush(new Workspace(unique,unique,"retired_"+unique));
        WorkspaceMember member=members.saveAndFlush(new WorkspaceMember(workspace,user,"ROLE_ADMIN"));
        jdbc.update("update users set verified_email=email,email_verified_at=now() where id=?",user.getId());
        return new Account(user.getId(),user.getEmail(),workspace.getId(),member.getId(),"Bearer "+jwt.generateToken(user.getId(),workspace.getId()));
    }
    private UUID pending(Account admin,Account target) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into workspace_invites(id,workspace_id,email,role,invited_by,invited_by_email,expires_at) values (?,?,?,'ROLE_VIEWER',?,?,now()+interval '1 day')",id,admin.workspace(),target.email(),admin.user(),admin.email());
        jdbc.update("insert into outbound_emails(id,purpose,recipient,invitation_id,encrypted_body,nonce) values (?,'WORKSPACE_INVITE',?,?,?,?)",UUID.randomUUID(),target.email(),id,new byte[]{1,2,3},new byte[12]);
        return id;
    }
    private UUID join(Account account,UUID workspace,String role) {
        UUID id=UUID.randomUUID(); jdbc.update("insert into workspace_members(id,workspace_id,user_id,role) values (?,?,?,?)",id,workspace,account.user(),role); return id;
    }
    private String selected(Account account,UUID workspace) { return "Bearer "+jwt.generateToken(account.user(),workspace); }
    private int role(String token,UUID member,String role,long version) throws Exception {
        return mvc.perform(patch("/api/v1/workspace/members/"+member+"/role").header("Authorization",token).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("role",role,"expectedVersion",version,"reason","Reviewed team access")))).andReturn().getResponse().getStatus();
    }
    private int remove(String token,UUID member,long version) throws Exception {
        return mvc.perform(post("/api/v1/workspace/members/"+member+"/remove").header("Authorization",token).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("expectedVersion",version,"reason","Reviewed team access")))).andReturn().getResponse().getStatus();
    }
    private long version(UUID id) { return jdbc.queryForObject("select version from workspace_members where id=?",Long.class,id); }
    private long adminCount(UUID ws) { return jdbc.queryForObject("select count(*) from workspace_members where workspace_id=? and role='ROLE_ADMIN'",Long.class,ws); }
    private long auditCount(UUID ws) { return jdbc.queryForObject("select count(*) from workspace_membership_audit where workspace_id=?",Long.class,ws); }
    private record Account(UUID user,String email,UUID workspace,UUID membership,String token) {}
}
