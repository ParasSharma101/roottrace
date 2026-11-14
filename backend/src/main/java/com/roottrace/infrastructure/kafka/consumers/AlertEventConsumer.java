package com.roottrace.infrastructure.kafka.consumers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.Alert;
import com.roottrace.domain.model.Incident;
import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.model.SeverityLevel;
import com.roottrace.domain.repository.AlertRepository;
import com.roottrace.domain.repository.IncidentRepository;
import com.roottrace.infrastructure.kafka.EventPublisher;
import com.roottrace.infrastructure.kafka.KafkaTopicConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class AlertEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertEventConsumer.class);
    private static final int DEDUPLICATION_WINDOW_MINUTES = 15;

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AlertEventConsumer(IncidentRepository incidentRepository,
                              AlertRepository alertRepository,
                              EventPublisher eventPublisher,
                              ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.INCOMING_ALERTS_TOPIC,
            containerFactory = "kafkaListenerContainerFactory",
            groupId = "roottrace-alert-processor"
    )
    @Transactional
    public void consumeAlert(ConsumerRecord<String, String> record) {
        log.info("Processing alert event from topic [{}] key [{}]", record.topic(), record.key());

        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});

            String source = (String) payload.getOrDefault("source", "Unknown");
            String serviceName = (String) payload.get("serviceName");
            String alertType = (String) payload.get("alertType");
            String severityStr = (String) payload.getOrDefault("severity", "SEV2");
            String triggeredAtStr = (String) payload.get("triggeredAt");
            Instant triggeredAt = triggeredAtStr != null ? Instant.parse(triggeredAtStr) : Instant.now();

            Object rawDetails = payload.get("rawPayload");
            String rawPayloadJson = rawDetails != null ? objectMapper.writeValueAsString(rawDetails) : "{}";

            // 1. Alert Storm Deduplication: Check if active incident already exists for this service in the last 15 mins
            Instant windowStart = Instant.now().minus(DEDUPLICATION_WINDOW_MINUTES, ChronoUnit.MINUTES);
            List<Incident> activeIncidents = incidentRepository.findActiveIncidentsForService(
                    List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING),
                    serviceName,
                    windowStart
            );

            Incident incident;
            if (!activeIncidents.isEmpty()) {
                incident = activeIncidents.get(0);
                log.warn("Alert storm deduplication: Attaching alert [{}] to existing active incident [{}] for service [{}]",
                        alertType, incident.getId(), serviceName);
            } else {
                SeverityLevel severity = parseSeverity(severityStr);
                String title = String.format("[Alert] %s: %s", serviceName, alertType);

                incident = new Incident(title, severity, IncidentStatus.OPEN);
                incident.setStartTime(triggeredAt);
                incident = incidentRepository.save(incident);
                log.info("Created new incident [{}] with severity [{}] for alert [{}]",
                        incident.getId(), severity, title);
            }

            // 2. Persist the Alert record linked to the incident
            Alert alert = new Alert(source, alertType, serviceName, rawPayloadJson, triggeredAt);
            alert.setIncident(incident);
            alertRepository.save(alert);

            log.info("Successfully persisted alert [{}] linked to incident [{}]", alert.getId(), incident.getId());

        } catch (Exception ex) {
            log.error("Failed to process alert record at partition {} offset {}. Routing to DLQ: {}",
                    record.partition(), record.offset(), ex.getMessage());
            eventPublisher.publish(
                    KafkaTopicConfig.INCOMING_ALERTS_DLQ_TOPIC,
                    record.key(),
                    record.value()
            );
        }
    }

    private SeverityLevel parseSeverity(String severityStr) {
        if (severityStr == null) return SeverityLevel.SEV2;
        try {
            return SeverityLevel.valueOf(severityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SeverityLevel.SEV2;
        }
    }
}
