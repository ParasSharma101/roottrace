package com.roottrace.domain;

import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RepositoryIntegrationTest extends com.roottrace.support.IntegrationTestSupport {

    @Autowired private IncidentRepository incidentRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private DeploymentRepository deploymentRepository;
    @Autowired private GitHubPRRepository gitHubPRRepository;
    @Autowired private LogEventRepository logEventRepository;
    @Autowired private HypothesisRepository hypothesisRepository;
    @Autowired private IncidentNoteRepository incidentNoteRepository;

    @Test
    @DisplayName("Should successfully persist and correlate Incident, Alert, Deployment, PR, Logs, and Hypotheses")
    void testEndToEndPersistenceAndWindowQuery() {
        Instant now = Instant.now();

        // 1. Create Incident
        Incident incident = new Incident(
                "Payment Service Outage: Connection Timeout",
                SeverityLevel.SEV1,
                IncidentStatus.OPEN
        );
        incident = incidentRepository.save(incident);
        assertThat(incident.getId()).isNotNull();

        // 2. Create Alert
        Alert alert = new Alert(
                "PagerDuty",
                "HighErrorRate",
                "payment-service",
                "{\"alert_id\": \"PD-1234\", \"threshold\": \"5% error rate exceeded\"}",
                now
        );
        alert.setIncident(incident);
        alert = alertRepository.save(alert);
        assertThat(alert.getId()).isNotNull();

        // 3. Create Deployment 10 minutes before the alert
        Deployment deployment = new Deployment(
                "payment-service",
                now.minus(10, ChronoUnit.MINUTES),
                "v2.4.1",
                "a1b2c3d4e5",
                "production"
        );
        deployment = deploymentRepository.save(deployment);
        assertThat(deployment.getId()).isNotNull();

        // 4. Create GitHub PR for the deployment
        GitHubPR pr = new GitHubPR(
                deployment,
                42,
                "refactor(payment): increase client timeout to 5000ms",
                "alice",
                "[\"PaymentClient.java\", \"application.yml\"]",
                "+ timeout: 5000ms\n- timeout: 200ms",
                now.minus(15, ChronoUnit.MINUTES)
        );
        pr = gitHubPRRepository.save(pr);
        assertThat(pr.getId()).isNotNull();

        // 5. Create LogEvents around the alert
        LogEvent errorLog = new LogEvent(
                "payment-service",
                now.minus(2, ChronoUnit.MINUTES),
                LogLevel.ERROR,
                "HikariPool-1 - Connection is not available, request timed out after 30000ms",
                "java.sql.SQLTransientConnectionException: HikariPool-1...",
                "{\"thread\": \"virtual-104\"}"
        );
        logEventRepository.save(errorLog);

        // 6. Create AI Hypothesis with evidence citation
        Hypothesis hypothesis = new Hypothesis(
                incident,
                "Hikari connection pool exhaustion caused by increased downstream client timeout holding connections.",
                new BigDecimal("0.95"),
                HypothesisStatus.PENDING,
                "[{\"type\": \"log\", \"ref_id\": \"" + errorLog.getId() + "\"}, {\"type\": \"pr\", \"ref_id\": \"" + pr.getId() + "\"}]"
        );
        hypothesis = hypothesisRepository.save(hypothesis);
        assertThat(hypothesis.getId()).isNotNull();

        // 7. Create Incident Note
        IncidentNote note = new IncidentNote(
                incident,
                hypothesis,
                "oncall-bob",
                "Verified hypothesis. Rolling back commit a1b2c3d4e5."
        );
        note = incidentNoteRepository.save(note);
        assertThat(note.getId()).isNotNull();

        // 8. Test Time-Window Slicing Query: [T - 30m, T + 5m]
        Instant windowStart = now.minus(30, ChronoUnit.MINUTES);
        Instant windowEnd = now.plus(5, ChronoUnit.MINUTES);

        List<Deployment> windowDeployments = deploymentRepository
                .findByWorkspaceIdAndServiceNameAndDeploymentTimeBetweenOrderByDeploymentTimeDesc(Workspace.LEGACY_WORKSPACE_ID, "payment-service", windowStart, windowEnd);
        assertThat(windowDeployments).hasSize(1);
        assertThat(windowDeployments.get(0).getVersionTag()).isEqualTo("v2.4.1");

        List<LogEvent> windowLogs = logEventRepository
                .findByWorkspaceIdAndServiceNameAndTimestampBetweenOrderByTimestampDesc(Workspace.LEGACY_WORKSPACE_ID, "payment-service", windowStart, windowEnd);
        assertThat(windowLogs).hasSize(1);
        assertThat(windowLogs.get(0).getLogLevel()).isEqualTo(LogLevel.ERROR);

        List<Hypothesis> hypotheses = hypothesisRepository.findByIncidentIdOrderByConfidenceScoreDesc(incident.getId());
        assertThat(hypotheses).hasSize(1);
        assertThat(hypotheses.get(0).getConfidenceScore()).isEqualByComparingTo("0.95");

        List<IncidentNote> notes = incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId());
        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getAuthor()).isEqualTo("oncall-bob");
    }
}
