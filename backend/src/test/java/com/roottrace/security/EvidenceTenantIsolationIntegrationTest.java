package com.roottrace.security;

import com.roottrace.correlation.service.CorrelationService;
import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EvidenceTenantIsolationIntegrationTest extends WorkspaceSessionTestSupport {
    @Autowired private CorrelationService correlation;
    @Autowired private IncidentRepository incidents;
    @Autowired private LogEventRepository logs;
    @Autowired private DeploymentRepository deployments;
    @Autowired private GitHubPRRepository prs;
    @Autowired private MockMvc mvc;

    @Test
    void overlappingEvidenceIsScopedInContextTimelineAndExport() throws Exception {
        Fixture owner = fixture("ROLE_ADMIN");
        Fixture foreign = fixture("ROLE_ADMIN");
        Instant now = Instant.now();
        Incident own = incident(owner.workspace(), now);
        evidence(owner.workspace(), now, "OWN_EVIDENCE");
        evidence(foreign.workspace(), now, "FOREIGN_SECRET");
        Workspace legacy = workspaces.getReferenceById(Workspace.LEGACY_WORKSPACE_ID);
        evidence(legacy, now, "LEGACY_SECRET");

        var bundle = correlation.buildContextBundle(own.getId());
        assertThat(bundle.workspaceId()).isEqualTo(owner.workspace().getId());
        assertThat(bundle.totalRawLogsCount()).isEqualTo(1);
        assertThat(bundle.deployments()).hasSize(1);
        assertThat(bundle.deployments().getFirst().versionTag()).isEqualTo("OWN_EVIDENCE");
        assertThat(bundle.deployments().getFirst().pullRequests()).hasSize(1);
        String context = correlation.buildContextBundleAsJson(own.getId());
        assertThat(context).contains("OWN_EVIDENCE").doesNotContain("FOREIGN_SECRET", "LEGACY_SECRET");

        String token = "Bearer " + tokens.generateToken(owner.user().getId(), owner.workspace().getId());
        String path = "/api/v1/incidents/" + own.getId();
        mvc.perform(get(path + "/timeline").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OWN_EVIDENCE")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
        mvc.perform(get(path + "/postmortem/export").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OWN_EVIDENCE")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));

        // Quarantine is also isolated in the reverse direction.
        var legacyBundle = correlation.buildContextBundle(incident(legacy, now).getId());
        assertThat(legacyBundle.totalRawLogsCount()).isEqualTo(1);
        assertThat(legacyBundle.deployments()).extracting("versionTag").containsExactly("LEGACY_SECRET");
    }

    @Test
    void unassignedIncidentCannotBuildContext() {
        Incident unassigned = incident(null, Instant.now());
        assertThatThrownBy(() -> correlation.buildContextBundle(unassigned.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no workspace");
    }

    private Incident incident(Workspace workspace, Instant time) {
        Incident incident = new Incident("[Alert] shared-service: failure", SeverityLevel.SEV1, IncidentStatus.OPEN);
        incident.setWorkspace(workspace);
        incident.setStartTime(time);
        return incidents.saveAndFlush(incident);
    }

    private void evidence(Workspace workspace, Instant time, String marker) {
        LogEvent log = new LogEvent("shared-service", time, LogLevel.ERROR, marker, null, null);
        log.setWorkspaceId(workspace.getId());
        logs.saveAndFlush(log);
        Deployment deployment = new Deployment("shared-service", time, marker, "commit", "production");
        deployment.setWorkspaceId(workspace.getId());
        deployment = deployments.saveAndFlush(deployment);
        prs.saveAndFlush(new GitHubPR(deployment, 1, marker, "author", "[]", marker, time));
    }
}
