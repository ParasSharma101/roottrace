package com.roottrace.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import com.roottrace.infrastructure.jobs.DurableTaskStore;
import com.roottrace.support.IntegrationTestSupport;
import com.roottrace.workspace.service.TaskRecoveryService;
import com.roottrace.workspace.service.TaskRecoveryService.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskRecoveryIntegrationTest extends IntegrationTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private WorkspaceRepository workspaces;
    @Autowired private WorkspaceMemberRepository memberships;
    @Autowired private IncidentRepository incidents;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private DurableTaskStore store;
    @Autowired private TaskRecoveryService service;
    @SpyBean private JdbcTemplate jdbc;

    @Test
    void redrivePreservesIdentityAndAuditsAuthoritativeActorAndFailureSnapshot() throws Exception {
        Account owner=account("ROLE_SRE");
        String longEmail="x".repeat(205)+UUID.randomUUID()+"@example.com";
        // 253 characters, within User.email and the audit snapshot's 255-character capacity.
        jdbc.update("update users set email=? where id=?",longEmail,owner.user().getId());
        Task task=task(owner,"PUBLISH","FAILED");
        UUID oldToken=UUID.randomUUID();
        jdbc.update("update durable_tasks set claim_token=?,lease_until=now()-interval '1 second' where id=?",oldToken,task.id());
        String payload=jdbc.queryForObject("select event_payload from durable_tasks where id=?",String.class,task.id());
        mvc.perform(post(path(task)+"/redrive").header("Authorization",owner.token()).contentType("application/json")
                .content("{\"reason\":\"  Restored the broker  \",\"expectedRedriveCount\":0,\"actorEmail\":\"spoofed@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.totalAttempts").value(5)).andExpect(jsonPath("$.redriveCount").value(1));
        assertThat(jdbc.queryForObject("select event_payload from durable_tasks where id=?",String.class,task.id())).isEqualTo(payload);
        var row=jdbc.queryForMap("select claim_token,lease_until,last_error_code,completed_at from durable_tasks where id=?",task.id());
        assertThat(row.values()).containsOnlyNulls();
        mvc.perform(get(path(task)+"/redrives").header("Authorization",owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].actorUserId").value(owner.user().getId().toString()))
                .andExpect(jsonPath("$.items[0].actorEmail").value(longEmail))
                .andExpect(jsonPath("$.items[0].reason").value("Restored the broker"))
                .andExpect(jsonPath("$.items[0].previousAttempts").value(5))
                .andExpect(jsonPath("$.items[0].totalAttemptsBefore").value(5))
                .andExpect(jsonPath("$.items[0].previousErrorCode").value("BrokerUnavailable"));
        // A completion from a pre-recovery worker cannot complete the new cycle.
        store.completePublish(new DurableTaskStore.Task(task.id(),owner.workspace().getId(),null,null,null,null,5,oldToken));
        assertThat(state(task.id())).isEqualTo("PENDING");
        jdbc.update("update durable_tasks set available_at='1970-01-01T00:00:00Z' where id=?",task.id());
        var claimed=store.claim("PUBLISH").orElseThrow();
        assertThat(claimed.id()).isEqualTo(task.id());
        assertThat(jdbc.queryForObject("select total_attempts from durable_tasks where id=?",Integer.class,task.id())).isEqualTo(6);
        store.fail(claimed,new IllegalStateException("not persisted"));
        assertThat(service.get(identity(owner),task.id()).totalAttempts()).isEqualTo(6);
    }

    @Test
    void concurrentAndStaleConfirmationsDoNotRepeatRecovery() throws Exception {
        Account owner=account("ROLE_ADMIN");
        Task task=task(owner,"INVESTIGATE","FAILED");
        try (var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(() -> retry(owner,task,0));
            var two=pool.submit(() -> retry(owner,task,0));
            assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(202,409);
        }
        assertThat(audits(task.id())).isEqualTo(1);
        // The same confirmation must remain stale even if this task has since failed again.
        jdbc.update("update durable_tasks set status='FAILED',attempts=5,total_attempts=10,completed_at=now(),last_error_code='ProviderUnavailable' where id=?",task.id());
        assertThat(retry(owner,task,0)).isEqualTo(409);
        assertThat(audits(task.id())).isEqualTo(1);
        assertThat(retry(owner,task,1)).isEqualTo(202);
        assertThat(audits(task.id())).isEqualTo(2);
        assertThat(service.get(identity(owner),task.id()).totalAttempts()).isEqualTo(10);
        assertThat(service.history(identity(owner),task.id(),null,1).items().getFirst().cycleNumber()).isEqualTo(2);
    }

    @Test
    void boundariesPermissionsValidationAndCurrentMembershipAreEnforced() throws Exception {
        Account owner=account("ROLE_ADMIN"), other=account("ROLE_SRE"), viewer=account("ROLE_VIEWER");
        Task task=task(owner,"INVESTIGATE","FAILED");
        for (String suffix:List.of("","/redrives")) {
            mvc.perform(get(path(task)+suffix).header("Authorization",other.token())).andExpect(status().isNotFound());
            mvc.perform(get(path(task)+suffix).header("Authorization",viewer.token())).andExpect(status().isForbidden());
        }
        mvc.perform(post(path(task)+"/redrive").header("Authorization",other.token()).contentType("application/json").content(body(0)))
                .andExpect(status().isNotFound());
        mvc.perform(post(path(task)+"/redrive").header("Authorization",viewer.token()).contentType("application/json").content(body(0)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(task)).header("X-RootTrace-Key","test-only-ingestion-key-at-least-32-bytes-long"))
                .andExpect(status().isUnauthorized());
        String accountToken="Bearer "+tokens.generateToken(owner.user().getId(),null);
        mvc.perform(get(path(task)).header("Authorization",accountToken)).andExpect(status().isForbidden());
        for (String invalid:List.of("{}","{\"reason\":\" \",\"expectedRedriveCount\":0}","{\"reason\":\"fixed\",\"expectedRedriveCount\":-1}",
                mapper.writeValueAsString(Map.of("reason","r".repeat(501),"expectedRedriveCount",0)))) {
            mvc.perform(post(path(task)+"/redrive").header("Authorization",owner.token()).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        for (String state:List.of("PENDING","RUNNING","SUCCEEDED")) {
            Task active=task(owner,"INVESTIGATE",state);
            assertThat(retry(owner,active,0)).isEqualTo(409);
            assertThat(audits(active.id())).isZero();
        }
        jdbc.update("update workspace_members set role='ROLE_VIEWER' where workspace_id=? and user_id=?",owner.workspace().getId(),owner.user().getId());
        assertThatThrownBy(() -> service.redrive(identity(owner),task.id(),new RedriveRequest("fixed",0)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        mvc.perform(get(path(task)).header("Authorization",owner.token())).andExpect(status().isForbidden());
        assertThat(audits(task.id())).isZero();
    }

    @Test
    void auditInsertAndQueueTransitionRollBackTogether() throws Exception {
        Account owner=account("ROLE_SRE");
        Task task=task(owner,"INVESTIGATE","FAILED");
        doThrow(new DataAccessResourceFailureException("simulated queue failure")).when(jdbc)
                .update(startsWith("update durable_tasks set status='PENDING',attempts=0"),any(Object[].class));
        assertThat(retry(owner,task,0)).isEqualTo(503);
        assertThat(state(task.id())).isEqualTo("FAILED");
        assertThat(audits(task.id())).isZero();
        assertThat(service.get(identity(owner),task.id()).redriveCount()).isZero();
    }

    @Test
    void cursorPaginationAndMetadataResponsesRemainScopedAndRedacted() throws Exception {
        Account owner=account("ROLE_SRE"), foreign=account("ROLE_ADMIN");
        List<UUID> expected=new ArrayList<>();
        for (int i=0;i<3;i++) expected.add(task(owner,"PUBLISH","FAILED").id());
        Task alien=task(foreign,"PUBLISH","FAILED");
        JsonNode first=mapper.readTree(mvc.perform(get("/api/v1/workspace/tasks?limit=2&kind=PUBLISH").header("Authorization",owner.token()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsString());
        assertThat(first.path("items")).hasSize(2);
        String secondJson=mvc.perform(get("/api/v1/workspace/tasks?limit=2&kind=PUBLISH&before="+first.path("nextCursor").asText())
                .header("Authorization",owner.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode second=mapper.readTree(secondJson);
        List<UUID> actual=new ArrayList<>();
        first.path("items").forEach(item -> actual.add(UUID.fromString(item.path("id").asText())));
        second.path("items").forEach(item -> actual.add(UUID.fromString(item.path("id").asText())));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(first.toString()+secondJson).doesNotContain("SENSITIVE_EVENT_PAYLOAD","claimToken","eventPayload","partitionKey");
        mvc.perform(get("/api/v1/workspace/tasks?before="+alien.id()).header("Authorization",owner.token())).andExpect(status().isNotFound());
        for (String query:List.of("limit=0","limit=101","status=UNKNOWN","kind=UNKNOWN")) {
            mvc.perform(get("/api/v1/workspace/tasks?"+query).header("Authorization",owner.token())).andExpect(status().isBadRequest());
        }
        Task investigation=task(owner,"INVESTIGATE","FAILED");
        assertThat(retry(owner,investigation,0)).isEqualTo(202);
        var history=service.history(identity(owner),investigation.id(),null,1);
        mvc.perform(get(path(alien)+"/redrives?before="+history.items().getFirst().id()).header("Authorization",foreign.token()))
                .andExpect(status().isNotFound());
    }

    private Account account(String role) {
        String id=UUID.randomUUID().toString();
        User user=users.save(new User(id+"@example.com","unused","Operator"));
        Workspace workspace=workspaces.save(new Workspace("Operations",id,"retired_"+id));
        memberships.save(new WorkspaceMember(workspace,user,role));
        return new Account(user,workspace,role,"Bearer "+tokens.generateToken(user.getId(),workspace.getId()));
    }
    private Task task(Account owner,String kind,String status) {
        UUID id=UUID.randomUUID(),incidentId=null,submissionId=null;
        if (kind.equals("INVESTIGATE")) {
            Incident incident=new Incident("Operator test",SeverityLevel.SEV1,IncidentStatus.OPEN);
            incident.setWorkspace(owner.workspace()); incidentId=incidents.saveAndFlush(incident).getId();
        } else {
            submissionId=UUID.randomUUID();
            jdbc.update("insert into ingestion_submissions(id,workspace_id,request_kind,payload_hash,item_count) values (?,?,'LOG','fixture',1)",submissionId,owner.workspace().getId());
        }
        int attempts=status.equals("FAILED")?5:1;
        jdbc.update("""
            insert into durable_tasks(id,workspace_id,kind,submission_id,incident_id,topic,partition_key,event_payload,status,attempts,total_attempts,last_error_code,completed_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,now())
            """,id,owner.workspace().getId(),kind,submissionId,incidentId,kind.equals("PUBLISH")?"raw-logs":null,
                kind.equals("PUBLISH")?"private-service":null,kind.equals("PUBLISH")?"SENSITIVE_EVENT_PAYLOAD":null,status,attempts,attempts,"BrokerUnavailable");
        return new Task(id);
    }
    private WorkspaceIdentity identity(Account owner) { return new WorkspaceIdentity(owner.user().getId(),"ignored@example.com","Ignored",owner.workspace().getId(),owner.role()); }
    private String path(Task task) { return "/api/v1/workspace/tasks/"+task.id(); }
    private String body(int expected) { return "{\"reason\":\"Fixed the cause\",\"expectedRedriveCount\":"+expected+"}"; }
    private int retry(Account owner,Task task,int expected) throws Exception {
        return mvc.perform(post(path(task)+"/redrive").header("Authorization",owner.token()).contentType("application/json").content(body(expected)))
                .andReturn().getResponse().getStatus();
    }
    private int audits(UUID id) { return jdbc.queryForObject("select count(*) from durable_task_redrives where task_id=?",Integer.class,id); }
    private String state(UUID id) { return jdbc.queryForObject("select status from durable_tasks where id=?",String.class,id); }
    private record Account(User user,Workspace workspace,String role,String token) {}
    private record Task(UUID id) {}
}
