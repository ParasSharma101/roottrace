package com.roottrace.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.Alert;
import com.roottrace.domain.model.Incident;
import com.roottrace.domain.model.LogEvent;
import com.roottrace.domain.repository.AlertRepository;
import com.roottrace.domain.repository.IncidentRepository;
import com.roottrace.domain.repository.LogEventRepository;
import com.roottrace.ingestion.dto.RawLogRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConsumerIntegrationTest extends com.roottrace.support.IntegrationTestSupport {

    @Autowired private EventPublisher eventPublisher;
    @Autowired private LogEventRepository logEventRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should consume batch logs from Kafka and persist into PostgreSQL via LogEventConsumer")
    void testBatchLogConsumption() throws Exception {
        String serviceName = "catalog-service-" + UUID.randomUUID();
        Instant now = Instant.now();

        // Publish 15 logs rapidly to raw-logs topic
        for (int i = 0; i < 15; i++) {
            RawLogRequest req = new RawLogRequest(
                    serviceName,
                    now.minus(i, ChronoUnit.SECONDS),
                    i % 2 == 0 ? "ERROR" : "INFO",
                    "Database query latency: " + (100 + i * 50) + "ms",
                    i % 2 == 0 ? "org.postgresql.util.PSQLException: QueryTimeout" : null,
                    Map.of("iteration", i)
            );
            eventPublisher.publish(KafkaTopicConfig.RAW_LOGS_TOPIC, serviceName, objectMapper.writeValueAsString(req));
        }

        // Wait up to 10s for the batch consumer to persist all 15 logs
        long deadline = System.currentTimeMillis() + 10000;
        List<LogEvent> persistedLogs = List.of();
        while (System.currentTimeMillis() < deadline) {
            persistedLogs = logEventRepository.findByServiceNameAndTimestampBetweenOrderByTimestampDesc(
                    serviceName,
                    now.minus(2, ChronoUnit.MINUTES),
                    now.plus(1, ChronoUnit.MINUTES)
            );
            if (persistedLogs.size() >= 15) {
                break;
            }
            Thread.sleep(300);
        }

        assertThat(persistedLogs).hasSize(15);
    }

    @Test
    @DisplayName("Should deduplicate multiple alerts for the same service into a single active Incident")
    void testAlertStormDeduplication() throws Exception {
        String serviceName = "billing-gateway-" + UUID.randomUUID();
        Instant now = Instant.now();

        // 1. Publish first alert: LatencySpike
        Map<String, Object> alert1 = Map.of(
                "source", "Datadog",
                "serviceName", serviceName,
                "alertType", "LatencySpike",
                "severity", "SEV1",
                "triggeredAt", now.toString(),
                "rawPayload", Map.of("p99", "4500ms")
        );
        eventPublisher.publish(KafkaTopicConfig.INCOMING_ALERTS_TOPIC, serviceName, objectMapper.writeValueAsString(alert1));

        // 2. Publish second alert: ConnectionExhaustion (200ms later for same service)
        Thread.sleep(200);
        Map<String, Object> alert2 = Map.of(
                "source", "PagerDuty",
                "serviceName", serviceName,
                "alertType", "DatabaseConnectionExhaustion",
                "severity", "SEV1",
                "triggeredAt", now.plus(1, ChronoUnit.SECONDS).toString(),
                "rawPayload", Map.of("active_connections", "100/100")
        );
        eventPublisher.publish(KafkaTopicConfig.INCOMING_ALERTS_TOPIC, serviceName, objectMapper.writeValueAsString(alert2));

        // Wait for both alerts to be processed
        long deadline = System.currentTimeMillis() + 10000;
        List<Alert> alerts = List.of();
        while (System.currentTimeMillis() < deadline) {
            alerts = alertRepository.findByServiceNameAndTriggeredAtBetweenOrderByTriggeredAtDesc(
                    serviceName,
                    now.minus(1, ChronoUnit.MINUTES),
                    now.plus(2, ChronoUnit.MINUTES)
            );
            if (alerts.size() >= 2) {
                break;
            }
            Thread.sleep(300);
        }

        assertThat(alerts).hasSize(2);

        // Core assertion: Both alerts MUST point to the same incident!
        Incident incidentAlert1 = alerts.get(0).getIncident();
        Incident incidentAlert2 = alerts.get(1).getIncident();

        assertThat(incidentAlert1).isNotNull();
        assertThat(incidentAlert2).isNotNull();
        assertThat(incidentAlert1.getId())
                .as("Alert storm deduplication must attach both alerts to the same Incident record")
                .isEqualTo(incidentAlert2.getId());
    }
}
