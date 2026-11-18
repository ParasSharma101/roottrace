package com.roottrace.security;

import com.fasterxml.jackson.databind.*;
import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.IncidentRepository;
import com.roottrace.workspace.service.QueueMonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class QueueMonitoringIntegrationTest extends WorkspaceSessionTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IncidentRepository incidents;
    @Autowired private QueueMonitoringService service;

    @Test
    void countsAgesAndSignalsUseOnlySelectedWorkspaceAndRespectRetryBackoff() throws Exception {
        Fixture owner=fixture("ROLE_SRE"), foreign=fixture("ROLE_ADMIN");
        task(owner,"PENDING",-600,null); task(owner,"PENDING",-60,null); task(owner,"PENDING",600,null);
        task(owner,"RUNNING",0,-60); task(owner,"RUNNING",0,60); task(owner,"FAILED",0,null); task(owner,"SUCCEEDED",0,null);
        task(foreign,"FAILED",0,null); task(foreign,"PENDING",-3600,null);
        JsonNode body=mapper.readTree(mvc.perform(get("/api/v1/workspace/queue-health").header("Authorization",token(owner)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsString());
        JsonNode queue=body.path("queues").get(0); // INVESTIGATE is the fixture kind.
        assertThat(queue.path("kind").asText()).isEqualTo("INVESTIGATE");
        assertThat(queue.path("pending").asInt()).isEqualTo(3);
        assertThat(queue.path("ready").asInt()).isEqualTo(2);
        assertThat(queue.path("overduePending").asInt()).isEqualTo(1);
        assertThat(queue.path("running").asInt()).isEqualTo(2);
        assertThat(queue.path("expiredLeases").asInt()).isEqualTo(1);
        assertThat(queue.path("failed").asInt()).isEqualTo(1);
        assertThat(queue.path("succeeded").asInt()).isEqualTo(1);
        assertThat(queue.path("oldestReadyAgeSeconds").asLong()).isGreaterThanOrEqualTo(600);
        assertThat(body.path("signals")).hasSize(3);
        assertThat(body.toString()).doesNotContain("payload", "claimToken", "workspaceId", "taskId", "actorEmail");
        assertThat(body.path("queues").get(1).path("pending").asInt()).isZero();
    }

    @Test
    void emptyQueueAndPermissionChangesAreHandledWithoutCrossTenantAccess() throws Exception {
        Fixture owner=fixture("ROLE_ADMIN"), viewer=fixture("ROLE_VIEWER");
        mvc.perform(get("/api/v1/workspace/queue-health").header("Authorization",token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.signals").isEmpty()).andExpect(jsonPath("$.queues.length()").value(2));
        mvc.perform(get("/api/v1/workspace/queue-health").header("Authorization",token(viewer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workspace/queue-health").header("X-RootTrace-Key","test-only-ingestion-key-at-least-32-bytes-long"))
                .andExpect(status().isUnauthorized());
        owner.member().setRole("ROLE_VIEWER"); memberships.saveAndFlush(owner.member());
        var identity=new WorkspaceIdentity(owner.user().getId(),owner.user().getEmail(),"Test",owner.workspace().getId(),"ROLE_ADMIN");
        assertThatThrownBy(() -> service.summary(identity)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void managementDiagnosticsAreNotPublicOrGrantedToWorkspaceAdministrators() throws Exception {
        Fixture admin=fixture("ROLE_ADMIN");
        for(String path:new String[]{"/actuator","/actuator/info","/actuator/metrics","/actuator/metrics/jvm.memory.used"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization",token(admin))).andExpect(status().isForbidden());
        }
        for(String path:new String[]{"/actuator/health","/actuator/health/liveness","/actuator/health/readiness"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());
        }
    }
    private String token(Fixture fixture) { return "Bearer "+tokens.generateToken(fixture.user().getId(),fixture.workspace().getId()); }
    private void task(Fixture owner,String status,int readyOffset,Integer leaseOffset) {
        Incident incident=new Incident("Queue fixture",SeverityLevel.SEV1,IncidentStatus.OPEN);
        incident.setWorkspace(owner.workspace()); incident=incidents.saveAndFlush(incident);
        jdbc.update("""
            insert into durable_tasks(id,workspace_id,kind,incident_id,status,available_at,lease_until)
            values (?,?,'INVESTIGATE',?,?,current_timestamp+(? * interval '1 second'),
                case when ?::integer is null then null else current_timestamp+(? * interval '1 second') end)
            """,UUID.randomUUID(),owner.workspace().getId(),incident.getId(),status,readyOffset,leaseOffset,leaseOffset);
    }
}
