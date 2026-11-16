package com.roottrace.ingestion.controller;

import jakarta.validation.Validator;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.roottrace.ingestion.service.WebhookEventPublisher;
import com.roottrace.security.IngestionIdentity;
import org.springframework.security.core.Authentication;
import com.roottrace.infrastructure.kafka.KafkaTopicConfig;
import com.roottrace.ingestion.dto.IngestionResponse;
import com.roottrace.ingestion.dto.RawLogRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/logs")
@CrossOrigin(origins = "*")
public class LogWebhookController {

    private final WebhookEventPublisher eventPublisher;
    private final Validator validator;

    public LogWebhookController(WebhookEventPublisher eventPublisher, Validator validator) {
        this.eventPublisher = eventPublisher;
        this.validator = validator;
    }

    @PostMapping
    public ResponseEntity<IngestionResponse> ingestSingleLog(@RequestBody @Valid RawLogRequest request, Authentication authentication) throws JsonProcessingException {
        UUID trackingId = UUID.randomUUID();

        eventPublisher.publish(
                KafkaTopicConfig.RAW_LOGS_TOPIC, "LOG", IngestionIdentity.requireWorkspace(authentication),
                request.serviceName(),
                request
        );

        return ResponseEntity.accepted().body(IngestionResponse.accepted(trackingId));
    }

    @PostMapping("/batch")
    public ResponseEntity<IngestionResponse> ingestBatchLogs(@RequestBody @Valid List<@Valid RawLogRequest> batch, Authentication authentication) throws JsonProcessingException {
        UUID trackingId = UUID.randomUUID();

        // Validate the entire batch before the first publication.
        if (batch.isEmpty() || batch.size() > 500 || batch.stream().anyMatch(item -> item == null || !validator.validate(item).isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid log batch (1-500 valid logs required)");
        }
        for (RawLogRequest logRequest : batch) {

            eventPublisher.publish(
                    KafkaTopicConfig.RAW_LOGS_TOPIC, "LOG", IngestionIdentity.requireWorkspace(authentication),
                    logRequest.serviceName(),
                    logRequest
            );
        }

        return ResponseEntity.accepted().body(IngestionResponse.accepted(trackingId, batch.size()));
    }
}
