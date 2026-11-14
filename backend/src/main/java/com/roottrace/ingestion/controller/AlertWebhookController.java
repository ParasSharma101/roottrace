package com.roottrace.ingestion.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.infrastructure.kafka.EventPublisher;
import com.roottrace.infrastructure.kafka.KafkaTopicConfig;
import com.roottrace.ingestion.dto.AlertWebhookRequest;
import com.roottrace.ingestion.dto.IngestionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/alerts")
public class AlertWebhookController {

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AlertWebhookController(EventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{source}")
    public ResponseEntity<IngestionResponse> ingestAlert(
            @PathVariable String source,
            @RequestBody @Valid AlertWebhookRequest request) throws JsonProcessingException {

        UUID trackingId = UUID.randomUUID();

        Map<String, Object> normalizedPayload = new HashMap<>();
        normalizedPayload.put("trackingId", trackingId.toString());
        normalizedPayload.put("source", source);
        normalizedPayload.put("serviceName", request.serviceName());
        normalizedPayload.put("alertType", request.alertType());
        normalizedPayload.put("severity", request.severity() != null ? request.severity() : "SEV2");
        normalizedPayload.put("triggeredAt", request.triggeredAt().toString());
        normalizedPayload.put("rawPayload", request.details() != null ? request.details() : Map.of());

        String payloadJson = objectMapper.writeValueAsString(normalizedPayload);

        eventPublisher.publish(
                KafkaTopicConfig.INCOMING_ALERTS_TOPIC,
                request.serviceName(),
                payloadJson
        );

        return ResponseEntity.accepted().body(IngestionResponse.accepted(trackingId));
    }
}
