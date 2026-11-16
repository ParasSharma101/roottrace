package com.roottrace.ingestion.controller;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.roottrace.ingestion.service.WebhookEventPublisher;
import com.roottrace.security.IngestionIdentity;
import org.springframework.security.core.Authentication;
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
@CrossOrigin(origins = "*")
public class AlertWebhookController {

    private final WebhookEventPublisher eventPublisher;

    public AlertWebhookController(WebhookEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/{source}")
    public ResponseEntity<IngestionResponse> ingestAlert(
            @PathVariable String source,
            @RequestBody @Valid AlertWebhookRequest request, Authentication authentication) throws JsonProcessingException {

        if (source.isBlank() || source.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid alert source");
        }
        UUID trackingId = UUID.randomUUID();

        Map<String, Object> normalizedPayload = new HashMap<>();
        normalizedPayload.put("trackingId", trackingId.toString());
        normalizedPayload.put("source", source);
        normalizedPayload.put("serviceName", request.serviceName());
        normalizedPayload.put("alertType", request.alertType());
        normalizedPayload.put("severity", request.severity() != null ? request.severity() : "SEV2");
        normalizedPayload.put("triggeredAt", request.triggeredAt().toString());
        normalizedPayload.put("rawPayload", request.details() != null ? request.details() : Map.of());

        eventPublisher.publish(
                KafkaTopicConfig.INCOMING_ALERTS_TOPIC, "ALERT", IngestionIdentity.requireWorkspace(authentication),
                request.serviceName(),
                normalizedPayload
        );

        return ResponseEntity.accepted().body(IngestionResponse.accepted(trackingId));
    }
}
