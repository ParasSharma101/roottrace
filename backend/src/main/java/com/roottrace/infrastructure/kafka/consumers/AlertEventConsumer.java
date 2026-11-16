package com.roottrace.infrastructure.kafka.consumers;

import com.roottrace.ingestion.service.IngestionEventCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.DateTimeException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.Alert;
import com.roottrace.domain.model.Incident;
import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.model.SeverityLevel;
import com.roottrace.domain.repository.AlertRepository;
import com.roottrace.domain.repository.IncidentRepository;
import com.roottrace.domain.repository.WorkspaceRepository;

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
    private final WorkspaceRepository workspaceRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final IngestionEventCodec codec;
    private final com.roottrace.infrastructure.jobs.DurableTaskStore tasks;
    private final com.roottrace.ingestion.service.IngestionInbox inbox;

    public AlertEventConsumer(IncidentRepository incidentRepository,
                              WorkspaceRepository workspaceRepository,
                              AlertRepository alertRepository,
                              EventPublisher eventPublisher,
                              ObjectMapper objectMapper,
                              com.roottrace.infrastructure.jobs.DurableTaskStore tasks,
                              IngestionEventCodec codec, com.roottrace.ingestion.service.IngestionInbox inbox) {
        this.incidentRepository = incidentRepository;
        this.workspaceRepository = workspaceRepository;
        this.alertRepository = alertRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.codec = codec;
        this.tasks = tasks;
        this.inbox = inbox;
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
            var decoded = codec.decode(record.value(), "ALERT");
            Map<String, Object> payload = objectMapper.convertValue(decoded.payload(), new TypeReference<>() {});

            String source = (String) payload.getOrDefault("source", "Unknown");
            String serviceName = (String) payload.get("serviceName");
            String alertType = (String) payload.get("alertType");
            String severityStr = (String) payload.getOrDefault("severity", "SEV2");
            String triggeredAtStr = (String) payload.get("triggeredAt");
            Instant triggeredAt = triggeredAtStr != null ? Instant.parse(triggeredAtStr) : Instant.now();

            if (serviceName == null || serviceName.isBlank() || serviceName.length() > 128
                    || alertType == null || alertType.isBlank() || alertType.length() > 128
                    || source == null || source.isBlank() || source.length() > 64) {
                throw new IllegalArgumentException("Invalid alert payload");
            }
            Object rawDetails = payload.get("rawPayload");
            String rawPayloadJson = rawDetails != null ? objectMapper.writeValueAsString(rawDetails) : "{}";

            if (!inbox.firstDelivery(decoded, "ALERT", record)) return;

            // 1. Alert Storm Deduplication: Check if active incident already exists for this service in the last 15 mins
            Instant windowStart = Instant.now().minus(DEDUPLICATION_WINDOW_MINUTES, ChronoUnit.MINUTES);
            List<Incident> activeIncidents = incidentRepository.findActiveIncidentsForService(
                    decoded.workspaceId(),
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
                if (title.length() > 255) title = title.substring(0, 255);

                incident = new Incident(title, severity, IncidentStatus.OPEN);
                // Ownership comes from server metadata; historical raw events remain legacy.
                incident.setWorkspace(workspaceRepository.getReferenceById(decoded.workspaceId()));
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

            // Enqueued in the same transaction as the incident, alert, and receipt.
            if (activeIncidents.isEmpty()) {
                alertRepository.flush();
                tasks.enqueueInvestigation(decoded.workspaceId(), incident.getId());
            }

        } catch (JsonProcessingException | IllegalArgumentException | ClassCastException | DateTimeException ex) {
            log.error("Failed to process alert record at partition {} offset {}. Routing to DLQ: {}",
                    record.partition(), record.offset(), ex.getMessage());
            eventPublisher.publish(
                    KafkaTopicConfig.INCOMING_ALERTS_DLQ_TOPIC,
                    record.key(),
                    record.value()
            ).join();
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
