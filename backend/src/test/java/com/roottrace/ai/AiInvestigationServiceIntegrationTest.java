package com.roottrace.ai;

import com.roottrace.ai.service.AiInvestigationService;
import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiInvestigationServiceIntegrationTest extends com.roottrace.support.IntegrationTestSupport {

    @Autowired private AiInvestigationService aiInvestigationService;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private DeploymentRepository deploymentRepository;
    @Autowired private GitHubPRRepository gitHubPRRepository;
    @Autowired private LogEventRepository logEventRepository;

    @Test
    @DisplayName("Scenario A: Bad DB Migration - Should correlate Flyway PR and HikariPool exhaustion logs into grounded hypothesis")
    void testScenarioABadDbMigration() {
        String serviceName = "order-service-" + UUID.randomUUID();
        Instant now = Instant.now();

        // 1. Incident and Alert: DatabaseConnectionExhaustion
        Incident incident = new Incident("[Alert] " + serviceName + ": DatabaseConnectionExhaustion", SeverityLevel.SEV1, IncidentStatus.OPEN);
        incident.setStartTime(now);
        incident.setWorkspace(workspaceRepository.getReferenceById(Workspace.LEGACY_WORKSPACE_ID));
        incident = incidentRepository.save(incident);

        Alert alert = new Alert("Datadog", "DatabaseConnectionExhaustion", serviceName, "{\"pool_exhausted\": true}", now);
        alert.setIncident(incident);
        alertRepository.save(alert);

        // 2. Deployment with Flyway migration PR
        Deployment deployment = new Deployment(serviceName, now.minus(10, ChronoUnit.MINUTES), "v2.0.4", "commit-db-1", "production");
        deployment = deploymentRepository.save(deployment);

        GitHubPR pr = new GitHubPR(
                deployment,
                101,
                "feat(orders): Flyway migration V4__alter_orders_exclusive_lock.sql",
                "david",
                "[\"V4__alter_orders_exclusive_lock.sql\"]",
                "ALTER TABLE orders ADD CONSTRAINT fk_user_status EXCLUSIVE;",
                now.minus(12, ChronoUnit.MINUTES)
        );
        pr = gitHubPRRepository.save(pr);

        // 3. Error logs: LockAcquisitionException & HikariPool-1 - Connection is not available
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            logs.add(new LogEvent(
                    serviceName,
                    now.minus(5, ChronoUnit.MINUTES).plusSeconds(i * 2),
                    LogLevel.ERROR,
                    "HikariPool-1 - Connection is not available, request timed out after 30000ms",
                    "org.postgresql.util.PSQLException: LockAcquisitionException: could not obtain lock on table orders",
                    "{\"thread\": \"loom-" + i + "\"}"
            ));
        }
        logEventRepository.saveAll(logs);

        // 4. Run AI Investigation
        List<Hypothesis> hypotheses = investigate(incident.getId());

        // 5. Assertions
        assertThat(hypotheses).isNotEmpty();

        Incident updatedIncident = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(updatedIncident.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(updatedIncident.getSummary()).contains(serviceName);

        // Primary hypothesis must be high confidence and cite both PR and Log
        Hypothesis primaryHypothesis = hypotheses.get(0);
        assertThat(primaryHypothesis.getStatus()).isEqualTo(HypothesisStatus.PENDING);
        assertThat(primaryHypothesis.getConfidenceScore()).isGreaterThanOrEqualTo(new java.math.BigDecimal("0.90"));
        assertThat(primaryHypothesis.getDescription()).contains("Flyway migration");
        assertThat(primaryHypothesis.getEvidenceCitations()).contains(pr.getId().toString());
    }

    @Test
    @DisplayName("Scenario B: Upstream Timeout - Should correlate client timeout PR and SocketTimeout logs into grounded hypothesis")
    void testScenarioBUpstreamTimeout() {
        String serviceName = "checkout-service-" + UUID.randomUUID();
        Instant now = Instant.now();

        // 1. Incident and Alert: CheckoutFlowLatencyHigh
        Incident incident = new Incident("[Alert] " + serviceName + ": CheckoutFlowLatencyHigh", SeverityLevel.SEV1, IncidentStatus.OPEN);
        incident.setStartTime(now);
        incident.setWorkspace(workspaceRepository.getReferenceById(Workspace.LEGACY_WORKSPACE_ID));
        incident = incidentRepository.save(incident);

        Alert alert = new Alert("PagerDuty", "CheckoutFlowLatencyHigh", serviceName, "{\"latency_p99\": \"5200ms\"}", now);
        alert.setIncident(incident);
        alertRepository.save(alert);

        // 2. Deployment with timeout PR
        Deployment deployment = new Deployment(serviceName, now.minus(8, ChronoUnit.MINUTES), "v1.9.0", "commit-timeout-2", "production");
        deployment = deploymentRepository.save(deployment);

        GitHubPR pr = new GitHubPR(
                deployment,
                202,
                "fix(client): increase payment gateway timeout to 5000ms",
                "elena",
                "[\"PaymentClientConfig.java\"]",
                "+ timeout = 5000ms\n- timeout = 200ms",
                now.minus(10, ChronoUnit.MINUTES)
        );
        pr = gitHubPRRepository.save(pr);

        // 3. Error logs: SocketTimeoutException
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            logs.add(new LogEvent(
                    serviceName,
                    now.minus(4, ChronoUnit.MINUTES).plusSeconds(i * 3),
                    LogLevel.ERROR,
                    "Payment processing failed: java.net.SocketTimeoutException: Read timed out",
                    "java.net.SocketTimeoutException: Read timed out\n\tat com.payment.client.HttpConnection.read(HttpConnection.java:42)",
                    "{\"orderId\": " + (5000 + i) + "}"
            ));
        }
        logEventRepository.saveAll(logs);

        // 4. Run AI Investigation
        List<Hypothesis> hypotheses = investigate(incident.getId());

        // 5. Assertions
        assertThat(hypotheses).isNotEmpty();

        Incident updatedIncident = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(updatedIncident.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);

        Hypothesis primaryHypothesis = hypotheses.get(0);
        assertThat(primaryHypothesis.getStatus()).isEqualTo(HypothesisStatus.PENDING);
        assertThat(primaryHypothesis.getConfidenceScore()).isGreaterThanOrEqualTo(new java.math.BigDecimal("0.90"));
        assertThat(primaryHypothesis.getDescription()).contains("timeout");
        assertThat(primaryHypothesis.getEvidenceCitations()).contains(pr.getId().toString());
    }
    private List<Hypothesis> investigate(UUID id) {
        var result = aiInvestigationService.evaluate(Workspace.LEGACY_WORKSPACE_ID, id);
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> aiInvestigationService.applyResult(Workspace.LEGACY_WORKSPACE_ID, id, result));
    }

}
