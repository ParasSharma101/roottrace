package com.roottrace.correlation;

import com.roottrace.correlation.model.CompressedLogSignature;
import com.roottrace.correlation.model.IncidentContextBundle;
import com.roottrace.correlation.service.CorrelationService;
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
@Transactional
class CorrelationServiceIntegrationTest extends com.roottrace.support.IntegrationTestSupport {

    @Autowired private CorrelationService correlationService;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private DeploymentRepository deploymentRepository;
    @Autowired private GitHubPRRepository gitHubPRRepository;
    @Autowired private LogEventRepository logEventRepository;

    @Test
    @DisplayName("Should accurately slice time window and compress repetitive error logs for AI prompt context")
    void testCorrelationAndLogCompression() throws Exception {
        String serviceName = "checkout-service-" + UUID.randomUUID();
        Instant alertTime = Instant.now();

        // 1. Create Incident & Alert
        Incident incident = new Incident("[Alert] " + serviceName + ": LatencyThresholdExceeded", SeverityLevel.SEV1, IncidentStatus.OPEN);
        incident.setStartTime(alertTime);
        incident.setWorkspace(workspaceRepository.getReferenceById(Workspace.LEGACY_WORKSPACE_ID));
        incident = incidentRepository.save(incident);

        Alert alert = new Alert("Datadog", "LatencyThresholdExceeded", serviceName, "{\"p99\": \"5200ms\"}", alertTime);
        alert.setIncident(incident);
        alertRepository.save(alert);

        // 2. Create Deployment 12 minutes before alert (inside window)
        Deployment deployment = new Deployment(
                serviceName,
                alertTime.minus(12, ChronoUnit.MINUTES),
                "v3.1.0",
                "c0ffee1234",
                "production"
        );
        deployment = deploymentRepository.save(deployment);

        GitHubPR pr = new GitHubPR(
                deployment,
                88,
                "refactor(client): update payment gateway timeout configuration",
                "carol",
                "[\"PaymentClientConfig.java\"]",
                "+ timeout = 5000ms\n- timeout = 200ms",
                alertTime.minus(15, ChronoUnit.MINUTES)
        );
        gitHubPRRepository.save(pr);

        // 3. Create Deployment 45 minutes before alert (outside 30-min window -> should NOT be included)
        Deployment oldDeployment = new Deployment(
                serviceName,
                alertTime.minus(45, ChronoUnit.MINUTES),
                "v3.0.0",
                "beef456789",
                "production"
        );
        deploymentRepository.save(oldDeployment);

        // 4. Create 30 repetitive error logs with varying Order IDs
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LogEvent log = new LogEvent(
                    serviceName,
                    alertTime.minus(10 - (i % 5), ChronoUnit.MINUTES),
                    LogLevel.ERROR,
                    "Order " + (1000 + i) + " failed: java.net.SocketTimeoutException: Read timed out to payment gateway",
                    "java.net.SocketTimeoutException: Read timed out\n\tat com.payment.client.HttpConnection.read(HttpConnection.java:42)",
                    "{\"orderId\": " + (1000 + i) + "}"
            );
            logs.add(log);
        }

        // Add 10 info logs
        for (int i = 0; i < 10; i++) {
            LogEvent log = new LogEvent(
                    serviceName,
                    alertTime.minus(8, ChronoUnit.MINUTES),
                    LogLevel.INFO,
                    "Checkout session " + (5000 + i) + " initiated by customer",
                    null,
                    null
            );
            logs.add(log);
        }
        logEventRepository.saveAll(logs);

        // 5. Execute Context Correlation
        IncidentContextBundle bundle = correlationService.buildContextBundle(incident.getId());

        // Assertions
        assertThat(bundle.incidentId()).isEqualTo(incident.getId());
        assertThat(bundle.serviceName()).isEqualTo(serviceName);
        assertThat(bundle.totalRawLogsCount()).isEqualTo(40);

        // Window filtering: only the deployment 12m ago should be in the window, not the 45m one!
        assertThat(bundle.deployments()).hasSize(1);
        assertThat(bundle.deployments().get(0).versionTag()).isEqualTo("v3.1.0");
        assertThat(bundle.deployments().get(0).pullRequests()).hasSize(1);
        assertThat(bundle.deployments().get(0).pullRequests().get(0).prNumber()).isEqualTo(88);

        // Log compression: 30 error logs must be compressed into 1 signature with count = 30
        List<CompressedLogSignature> compressedLogs = bundle.compressedLogs();
        assertThat(compressedLogs).hasSize(2); // 1 error signature, 1 info signature

        CompressedLogSignature errorSig = compressedLogs.stream()
                .filter(s -> s.logLevel() == LogLevel.ERROR)
                .findFirst()
                .orElseThrow();

        assertThat(errorSig.count()).isEqualTo(30);
        assertThat(errorSig.signature()).contains("java.net.SocketTimeoutException: Read timed out");
        assertThat(errorSig.sampleLogId()).isNotNull();

        // Verify JSON serialization works cleanly
        String json = correlationService.buildContextBundleAsJson(incident.getId());
        assertThat(json).contains("checkout-service");
        assertThat(json).contains("v3.1.0");
        assertThat(json).contains("count\" : 30");
    }
}
