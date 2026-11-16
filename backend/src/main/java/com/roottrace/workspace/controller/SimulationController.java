package com.roottrace.workspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.Deployment;
import com.roottrace.domain.model.GitHubPR;
import com.roottrace.domain.model.LogEvent;
import com.roottrace.domain.model.LogLevel;
import com.roottrace.domain.repository.DeploymentRepository;
import com.roottrace.domain.repository.GitHubPRRepository;
import com.roottrace.domain.repository.LogEventRepository;
import com.roottrace.infrastructure.kafka.EventPublisher;
import com.roottrace.infrastructure.kafka.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@Profile("dev & !prod")
@ConditionalOnProperty(name = "roottrace.demo.enabled", havingValue = "true")
@RequestMapping("/api/v1/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);

    private final DeploymentRepository deploymentRepository;
    private final GitHubPRRepository gitHubPRRepository;
    private final LogEventRepository logEventRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public SimulationController(DeploymentRepository deploymentRepository,
                                GitHubPRRepository gitHubPRRepository,
                                LogEventRepository logEventRepository,
                                EventPublisher eventPublisher,
                                ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.gitHubPRRepository = gitHubPRRepository;
        this.logEventRepository = logEventRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/scenario-a")
    public ResponseEntity<Map<String, Object>> simulateScenarioA() throws Exception {
        String serviceName = "payment-service-" + (System.currentTimeMillis() % 100000);
        Instant now = Instant.now();

        // 1. Pre-seed Deployment (T - 12m)
        Deployment dep = deploymentRepository.save(new Deployment(
                serviceName,
                now.minus(12, ChronoUnit.MINUTES),
                "v2.4.1",
                "c98f" + UUID.randomUUID().toString().substring(0, 8),
                "production"
        ));

        // 2. Pre-seed Correlated GitHub PR (T - 11m)
        gitHubPRRepository.save(new GitHubPR(
                dep,
                42,
                "Add database migration for billing index",
                "charlie",
                "[\"V2__add_index.sql\"]",
                "ALTER TABLE payments ADD COLUMN billing_cycle_id INT; CREATE INDEX idx_payments_billing ON payments(billing_cycle_id);",
                now.minus(11, ChronoUnit.MINUTES)
        ));

        // 3. Pre-seed Correlated Error Logs (T - 5m to T - 1m)
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            logs.add(new LogEvent(
                    serviceName,
                    now.minus(5 - i, ChronoUnit.MINUTES),
                    LogLevel.ERROR,
                    "Connection pool exhausted: timeout waiting for connection (HikariPool-1)",
                    "org.postgresql.util.PSQLException: LockAcquisitionException: could not obtain lock on table payments\n\tat com.payment.service.OrderDao.execute(OrderDao.java:142)\n\tat com.payment.service.PaymentProcessor.process(PaymentProcessor.java:88)",
                    "{\"thread\": \"loom-" + i + "\"}"
            ));
        }
        logEventRepository.saveAll(logs);

        // 4. Publish Alert triggering Incident Creation & Gemini AI Investigation
        Map<String, Object> alertPayload = new HashMap<>();
        alertPayload.put("trackingId", UUID.randomUUID().toString());
        alertPayload.put("source", "Datadog");
        alertPayload.put("serviceName", serviceName);
        alertPayload.put("alertType", "ConnectionPoolExhausted");
        alertPayload.put("severity", "SEV1");
        alertPayload.put("triggeredAt", now.toString());
        alertPayload.put("rawPayload", Map.of(
                "description", "Database connection pool exhausted. 99% acquisition timeout rate.",
                "metrics", Map.of("p99_latency_ms", 12400, "pool_saturation", 1.0)
        ));

        eventPublisher.publish(
                KafkaTopicConfig.INCOMING_ALERTS_TOPIC,
                serviceName,
                objectMapper.writeValueAsString(alertPayload)
        );

        log.info("Simulated Scenario A (DB Migration Lock Contention) for service [{}]", serviceName);
        return ResponseEntity.ok(Map.of(
                "status", "TRIGGERED",
                "scenario", "SCENARIO_A",
                "serviceName", serviceName,
                "message", "Scenario A simulated. Alert published to Kafka. AI correlation and Gemini investigation started."
        ));
    }

    @PostMapping("/scenario-b")
    public ResponseEntity<Map<String, Object>> simulateScenarioB() throws Exception {
        String serviceName = "shipping-service-" + (System.currentTimeMillis() % 100000);
        Instant now = Instant.now();

        // 1. Pre-seed Deployment (T - 15m)
        Deployment dep = deploymentRepository.save(new Deployment(
                serviceName,
                now.minus(15, ChronoUnit.MINUTES),
                "v1.9.0",
                "a12b" + UUID.randomUUID().toString().substring(0, 8),
                "production"
        ));

        // 2. Pre-seed Correlated GitHub PR (T - 14m)
        gitHubPRRepository.save(new GitHubPR(
                dep,
                108,
                "Bump FedEx shipping API client timeout configuration",
                "dave",
                "[\"application.yml\", \"FedExClient.java\"]",
                "Changed carrier.fedex.timeout from 2000ms to 10000ms without fallback",
                now.minus(14, ChronoUnit.MINUTES)
        ));

        // 3. Pre-seed Correlated Error Logs (T - 4m)
        List<LogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            logs.add(new LogEvent(
                    serviceName,
                    now.minus(4 - i, ChronoUnit.MINUTES),
                    LogLevel.ERROR,
                    "Downstream HTTP call to FedEx carrier gateway timed out after 10000ms",
                    "java.net.SocketTimeoutException: Read timed out to https://api.fedex.com/v2/shipments\n\tat com.shipping.client.CarrierClient.post(CarrierClient.java:94)",
                    "{\"client\": \"feign-fedex\", \"timeout_ms\": 10000}"
            ));
        }
        logEventRepository.saveAll(logs);

        // 4. Publish Alert
        Map<String, Object> alertPayload = new HashMap<>();
        alertPayload.put("trackingId", UUID.randomUUID().toString());
        alertPayload.put("source", "Datadog");
        alertPayload.put("serviceName", serviceName);
        alertPayload.put("alertType", "UpstreamDependencyTimeout");
        alertPayload.put("severity", "SEV2");
        alertPayload.put("triggeredAt", now.toString());
        alertPayload.put("rawPayload", Map.of(
                "description", "Downstream HTTP call to FedEx API timing out after 10000ms",
                "metrics", Map.of("error_rate", 0.42, "timeout_count", 85)
        ));

        eventPublisher.publish(
                KafkaTopicConfig.INCOMING_ALERTS_TOPIC,
                serviceName,
                objectMapper.writeValueAsString(alertPayload)
        );

        log.info("Simulated Scenario B (Upstream Dependency Timeout) for service [{}]", serviceName);
        return ResponseEntity.ok(Map.of(
                "status", "TRIGGERED",
                "scenario", "SCENARIO_B",
                "serviceName", serviceName,
                "message", "Scenario B simulated. Alert published to Kafka. AI correlation and Gemini investigation started."
        ));
    }
}
