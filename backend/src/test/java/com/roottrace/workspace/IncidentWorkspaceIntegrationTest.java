package com.roottrace.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import com.roottrace.workspace.dto.AddNoteRequest;
import com.roottrace.workspace.dto.RejectHypothesisRequest;
import com.roottrace.workspace.dto.UpdateIncidentStatusRequest;
import com.roottrace.workspace.dto.VerifyHypothesisRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "alice@roottrace.io", roles = {"SRE", "VIEWER"})
class IncidentWorkspaceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private IncidentRepository incidentRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private DeploymentRepository deploymentRepository;
    @Autowired private GitHubPRRepository gitHubPRRepository;
    @Autowired private LogEventRepository logEventRepository;
    @Autowired private HypothesisRepository hypothesisRepository;
    @Autowired private IncidentNoteRepository incidentNoteRepository;

    @Test
    @DisplayName("GET /api/v1/incidents - should list incidents with status and severity filters")
    void testListIncidentsWithFilters() throws Exception {
        String testService = "order-service-" + UUID.randomUUID();

        Incident inc1 = incidentRepository.save(new Incident("[Alert] " + testService + ": High5xxRate", SeverityLevel.SEV1, IncidentStatus.INVESTIGATING));
        Incident inc2 = incidentRepository.save(new Incident("[Alert] " + testService + ": LatencySpike", SeverityLevel.SEV2, IncidentStatus.RESOLVED));

        Alert alert1 = new Alert(inc1, "Datadog", "High5xxRate", testService, "{}", Instant.now());
        alertRepository.save(alert1);

        Alert alert2 = new Alert(inc2, "Datadog", "LatencySpike", testService, "{}", Instant.now());
        alertRepository.save(alert2);

        // Filter by status INVESTIGATING
        mockMvc.perform(get("/api/v1/incidents")
                        .param("status", "INVESTIGATING")
                        .param("serviceName", testService))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(inc1.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("INVESTIGATING"))
                .andExpect(jsonPath("$[0].severityLevel").value("SEV1"))
                .andExpect(jsonPath("$[0].serviceName").value(testService));

        // Filter by severity SEV2
        mockMvc.perform(get("/api/v1/incidents")
                        .param("severity", "SEV2")
                        .param("serviceName", testService))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(inc2.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"));
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id} - should return complete incident details")
    void testGetIncidentDetail() throws Exception {
        String testService = "auth-service-" + UUID.randomUUID();
        Incident inc = incidentRepository.save(new Incident("[Alert] " + testService + ": AuthFailuresHigh", SeverityLevel.SEV1, IncidentStatus.INVESTIGATING));

        alertRepository.save(new Alert(inc, "Datadog", "AuthFailuresHigh", testService, "{\"threshold\": 50}", Instant.now()));

        Hypothesis hyp = hypothesisRepository.save(new Hypothesis(
                inc,
                "Expired JWT signing certificate",
                new BigDecimal("0.92"),
                HypothesisStatus.PENDING,
                "[{\"type\":\"LOG\",\"referenceId\":\"log-1\"}]"
        ));

        incidentNoteRepository.save(new IncidentNote(
                inc,
                hyp,
                "alice@company.com",
                "Rotating JWT cert now"
        ));

        mockMvc.perform(get("/api/v1/incidents/{id}", inc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inc.getId().toString()))
                .andExpect(jsonPath("$.serviceName").value(testService))
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].alertType").value("AuthFailuresHigh"))
                .andExpect(jsonPath("$.hypotheses", hasSize(1)))
                .andExpect(jsonPath("$.hypotheses[0].description").value("Expired JWT signing certificate"))
                .andExpect(jsonPath("$.notes", hasSize(1)))
                .andExpect(jsonPath("$.notes[0].author").value("alice@company.com"));
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id}/timeline - should aggregate all 6 timeline event types chronologically")
    void testGetTimeline() throws Exception {
        String testService = "billing-service-" + UUID.randomUUID();
        Instant now = Instant.now();

        Incident inc = incidentRepository.save(new Incident("[Alert] " + testService + ": DatabaseTimeout", SeverityLevel.SEV1, IncidentStatus.INVESTIGATING));
        inc.setStartTime(now);
        incidentRepository.save(inc);

        // 1. Deployment (T - 15m)
        Deployment dep = deploymentRepository.save(new Deployment(
                testService,
                now.minus(15, ChronoUnit.MINUTES),
                "v2.1.0",
                "abc987654321",
                "production"
        ));

        // 2. GitHub PR (T - 14m)
        gitHubPRRepository.save(new GitHubPR(
                dep,
                101,
                "Add database migration for billing index",
                "charlie",
                "[\"V2__add_index.sql\"]",
                "Added lock on billing table",
                now.minus(14, ChronoUnit.MINUTES)
        ));

        // 3. Alert (T)
        alertRepository.save(new Alert(
                inc,
                "Datadog",
                "DatabaseTimeout",
                testService,
                "{\"metric\": \"p99\"}",
                now
        ));

        // 4. Error Log (T + 1m)
        logEventRepository.save(new LogEvent(
                testService,
                now.plus(1, ChronoUnit.MINUTES),
                LogLevel.ERROR,
                "Connection pool exhausted: timeout waiting for connection",
                "org.postgresql.util.PSQLException: timeout\n\tat com.billing.Repo.get()",
                "{}"
        ));

        // 5. AI Hypothesis (T + 2m)
        Hypothesis hyp = hypothesisRepository.save(new Hypothesis(
                inc,
                "Database lock contention from migration script",
                new BigDecimal("0.95"),
                HypothesisStatus.PENDING,
                "[]"
        ));

        // 6. Human Action Note (T + 3m)
        incidentNoteRepository.save(new IncidentNote(
                inc,
                hyp,
                "oncall@company.com",
                "Killing exclusive lock process in PostgreSQL"
        ));

        mockMvc.perform(get("/api/v1/incidents/{id}/timeline", inc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$[*].eventType", hasItems("DEPLOYMENT", "GITHUB_PR", "ALERT", "LOG_ERROR", "AI_HYPOTHESIS", "HUMAN_ACTION")));
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/hypotheses/{hypId}/verify and reject - should update status and create audit notes")
    void testVerifyAndRejectHypothesis() throws Exception {
        String testService = "payment-gateway-" + UUID.randomUUID();
        Incident inc = incidentRepository.save(new Incident("[Alert] " + testService + ": PaymentDrop", SeverityLevel.SEV1, IncidentStatus.INVESTIGATING));

        Hypothesis hyp1 = hypothesisRepository.save(new Hypothesis(
                inc,
                "Stripe API timeout",
                new BigDecimal("0.85"),
                HypothesisStatus.PENDING,
                "[]"
        ));

        Hypothesis hyp2 = hypothesisRepository.save(new Hypothesis(
                inc,
                "TLS cert expired",
                new BigDecimal("0.40"),
                HypothesisStatus.PENDING,
                "[]"
        ));

        // Verify hyp1
        VerifyHypothesisRequest verifyReq = new VerifyHypothesisRequest("bob@company.com");
        mockMvc.perform(post("/api/v1/incidents/{id}/hypotheses/{hypId}/verify", inc.getId(), hyp1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        Hypothesis reloaded1 = hypothesisRepository.findById(hyp1.getId()).orElseThrow();
        assertThat(reloaded1.getStatus()).isEqualTo(HypothesisStatus.VERIFIED);

        // Reject hyp2
        RejectHypothesisRequest rejectReq = new RejectHypothesisRequest("bob@company.com", "Certificates are valid until 2027");
        mockMvc.perform(post("/api/v1/incidents/{id}/hypotheses/{hypId}/reject", inc.getId(), hyp2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Hypothesis reloaded2 = hypothesisRepository.findById(hyp2.getId()).orElseThrow();
        assertThat(reloaded2.getStatus()).isEqualTo(HypothesisStatus.REJECTED);

        // Verify audit notes were automatically appended
        List<IncidentNote> notes = incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(inc.getId());
        assertThat(notes).hasSize(2);
        assertThat(notes.get(0).getNote()).contains("Verified Root Cause Hypothesis");
        assertThat(notes.get(1).getNote()).contains("Rejected Hypothesis");
    }

    @Test
    @DisplayName("PATCH /api/v1/incidents/{id}/status and POST note - should update status and record notes")
    void testUpdateStatusAndAddNotes() throws Exception {
        String testService = "user-service-" + UUID.randomUUID();
        Incident inc = incidentRepository.save(new Incident("[Alert] " + testService + ": CrashLoop", SeverityLevel.SEV1, IncidentStatus.INVESTIGATING));

        // Add engineer note
        AddNoteRequest noteReq = new AddNoteRequest("dave@company.com", "Restarted deployment pods", null);
        mockMvc.perform(post("/api/v1/incidents/{id}/notes", inc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("dave@company.com"))
                .andExpect(jsonPath("$.note").value("Restarted deployment pods"));

        // Transition status to RESOLVED
        UpdateIncidentStatusRequest statusReq = new UpdateIncidentStatusRequest(IncidentStatus.RESOLVED);
        mockMvc.perform(patch("/api/v1/incidents/{id}/status", inc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionTime").isNotEmpty());

        Incident reloaded = incidentRepository.findById(inc.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(reloaded.getResolutionTime()).isNotNull();
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id}/postmortem - should generate structured Markdown postmortem")
    void testGeneratePostmortem() throws Exception {
        String testService = "search-service-" + UUID.randomUUID();
        Incident inc = incidentRepository.save(new Incident("[Alert] " + testService + ": ElasticsearchClusterDegraded", SeverityLevel.SEV1, IncidentStatus.INVESTIGATING));

        alertRepository.save(new Alert(inc, "Prometheus", "ElasticsearchClusterDegraded", testService, "{}", Instant.now()));

        hypothesisRepository.save(new Hypothesis(
                inc,
                "Unbounded index query causing JVM heap exhaustion",
                new BigDecimal("0.96"),
                HypothesisStatus.VERIFIED,
                "[{\"type\":\"LOG\",\"referenceId\":\"log-99\"}]"
        ));

        // Transition to RESOLVED
        inc.setStatus(IncidentStatus.RESOLVED);
        inc.setResolutionTime(Instant.now());
        incidentRepository.save(inc);

        // JSON postmortem endpoint
        mockMvc.perform(get("/api/v1/incidents/{id}/postmortem", inc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(inc.getId().toString()))
                .andExpect(jsonPath("$.markdownContent").value(containsString("# RootTrace Incident Postmortem")))
                .andExpect(jsonPath("$.markdownContent").value(containsString("Unbounded index query causing JVM heap exhaustion")))
                .andExpect(jsonPath("$.markdownContent").value(containsString("VERIFIED")))
                .andExpect(jsonPath("$.markdownContent").value(containsString("96%")));

        // Export markdown endpoint
        mockMvc.perform(get("/api/v1/incidents/{id}/postmortem/export", inc.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"postmortem-" + inc.getId() + ".md\"")))
                .andExpect(content().string(containsString("# RootTrace Incident Postmortem")));
    }
}
