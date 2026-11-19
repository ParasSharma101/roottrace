package com.roottrace.security;

import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentTenantIsolationIntegrationTest extends WorkspaceSessionTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private IncidentRepository incidents;
    @Autowired private HypothesisRepository hypotheses;
    @Autowired private IncidentNoteRepository notes;
    @Autowired private LogEventRepository logs;
    @Autowired private DeploymentRepository deployments;

    @Test
    void unscopedTelemetryIsExcludedFromNormalTimelineAndExport() throws Exception {
        Fixture owner = fixture("ROLE_ADMIN");
        Incident own = incident(owner.workspace());
        logs.saveAndFlush(new LogEvent("same-service", java.time.Instant.now(), LogLevel.ERROR,
                "UNSCOPED_SECRET_LOG", null, null));
        deployments.saveAndFlush(new Deployment("same-service", java.time.Instant.now(),
                "UNSCOPED_SECRET_DEPLOYMENT", "commit", "production"));
        String path = "/api/v1/incidents/" + own.getId();
        mvc.perform(get(path + "/timeline").header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(path + "/postmortem/export").header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("UNSCOPED_SECRET"))));
    }

    @Test
    void listAndAllReadsExcludeForeignUnassignedAndLegacyIncidents() throws Exception {
        Fixture owner = fixture("ROLE_ADMIN");
        Fixture foreign = fixture("ROLE_ADMIN");
        Incident own = incident(owner.workspace());
        Incident other = incident(foreign.workspace());
        incident(null);
        incident(workspaces.getReferenceById(AuthDataSeeder.DEFAULT_WORKSPACE_ID));
        String token = token(owner);
        mvc.perform(get("/api/v1/incidents").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(own.getId().toString()));
        for (UUID id : new UUID[]{other.getId(), UUID.randomUUID()}) {
            for (String suffix : new String[]{"", "/timeline", "/postmortem", "/postmortem/export"}) {
                mvc.perform(get("/api/v1/incidents/" + id + suffix).header("Authorization", token))
                        .andExpect(status().isNotFound());
            }
        }
    }

    @Test
    void foreignMutationsReturnNotFoundAndDoNotChangeData() throws Exception {
        Fixture owner = fixture("ROLE_ADMIN");
        Incident foreign = incident(fixture("ROLE_ADMIN").workspace());
        Hypothesis hypothesis = hypothesis(foreign);
        String path = "/api/v1/incidents/" + foreign.getId();
        String token = token(owner);
        mvc.perform(patch(path + "/status").header("Authorization", token).contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(path + "/notes").header("Authorization", token).contentType("application/json")
                        .content("{\"author\":\"spoofed\",\"note\":\"unauthorized\"}"))
                .andExpect(status().isNotFound());
        for (String action : new String[]{"verify", "reject"}) {
            mvc.perform(post(path + "/hypotheses/" + hypothesis.getId() + "/" + action)
                            .header("Authorization", token).contentType("application/json")
                            .content("{\"reason\":\"unauthorized\"}"))
                    .andExpect(status().isNotFound());
        }
        assertThat(foreign.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(hypothesis.getStatus()).isEqualTo(HypothesisStatus.PENDING);
        assertThat(notes.findByIncidentIdOrderByCreatedAtAsc(foreign.getId())).isEmpty();
    }

    @Test
    void childLinksMustBelongToTheSameIncidentAndAuditAuthorIsTrusted() throws Exception {
        Fixture owner = fixture("ROLE_ADMIN");
        Incident own = incident(owner.workspace());
        Hypothesis wrongIncident = hypothesis(incident(owner.workspace()));
        Hypothesis foreign = hypothesis(incident(fixture("ROLE_ADMIN").workspace()));
        String path = "/api/v1/incidents/" + own.getId();
        for (Hypothesis wrong : new Hypothesis[]{wrongIncident, foreign}) {
            mvc.perform(post(path + "/notes").header("Authorization", token(owner)).contentType("application/json")
                            .content("{\"author\":\"spoofed\",\"note\":\"test\",\"hypothesisId\":\"" + wrong.getId() + "\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(post(path + "/hypotheses/" + wrong.getId() + "/verify")
                            .header("Authorization", token(owner)).contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post(path + "/notes").header("Authorization", token(owner)).contentType("application/json")
                        .content("{\"author\":\"spoofed\",\"note\":\"legitimate note\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.author").value(owner.user().getEmail()));
        assertThat(notes.findByIncidentIdOrderByCreatedAtAsc(own.getId())).hasSize(1);
    }

    @Test
    void legacyMembershipCannotBeSelectedOrUsedOutsideDevelopment() throws Exception {
        Fixture owner = fixture("ROLE_ADMIN");
        Workspace legacy = workspaces.findById(AuthDataSeeder.DEFAULT_WORKSPACE_ID).orElseThrow();
        assertThat(legacy.isLegacy()).isTrue();
        memberships.saveAndFlush(new WorkspaceMember(legacy, owner.user(), "ROLE_ADMIN"));
        String account = "Bearer " + tokens.generateToken(owner.user().getId(), null);
        mvc.perform(get("/api/v1/auth/workspaces").header("Authorization", account))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/v1/auth/select-workspace").header("Authorization", account).contentType("application/json")
                        .content("{\"workspaceId\":\"" + legacy.getId() + "\"}"))
                .andExpect(status().isNotFound());
        String selected = "Bearer " + tokens.generateToken(owner.user().getId(), legacy.getId());
        mvc.perform(get("/api/v1/incidents").header("Authorization", selected)).andExpect(status().isUnauthorized());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev", "prod");
        assertThat(new WorkspaceAccessPolicy(environment).canAccess(legacy)).isFalse();
        environment.setActiveProfiles("dev");
        assertThat(new WorkspaceAccessPolicy(environment).canAccess(legacy)).isTrue();
    }

    private String token(Fixture owner) {
        return "Bearer " + tokens.generateToken(owner.user().getId(), owner.workspace().getId());
    }

    private Incident incident(Workspace workspace) {
        Incident incident = new Incident("[Alert] same-service: failure", SeverityLevel.SEV1, IncidentStatus.OPEN);
        incident.setWorkspace(workspace);
        return incidents.saveAndFlush(incident);
    }

    private Hypothesis hypothesis(Incident incident) {
        return hypotheses.saveAndFlush(new Hypothesis(incident, "Test hypothesis", new BigDecimal("0.50"), HypothesisStatus.PENDING, "[]"));
    }
}
