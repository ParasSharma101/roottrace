package com.roottrace.ingestion.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.infrastructure.kafka.EventPublisher;
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
public class LogWebhookController {

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public LogWebhookController(EventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<IngestionResponse> ingestSingleLog(@RequestBody @Valid RawLogRequest request) throws JsonProcessingException {
        UUID trackingId = UUID.randomUUID();
        String payloadJson = objectMapper.writeValueAsString(request);

        eventPublisher.publish(
                KafkaTopicConfig.RAW_LOGS_TOPIC,
                request.serviceName(),
                payloadJson
        );

        return ResponseEntity.accepted().body(IngestionResponse.accepted(trackingId));
    }

    @PostMapping("/batch")
    public ResponseEntity<IngestionResponse> ingestBatchLogs(@RequestBody @Valid List<@Valid RawLogRequest> batch) throws JsonProcessingException {
        UUID trackingId = UUID.randomUUID();

        for (RawLogRequest logRequest : batch) {
            String payloadJson = objectMapper.writeValueAsString(logRequest);
            eventPublisher.publish(
                    KafkaTopicConfig.RAW_LOGS_TOPIC,
                    logRequest.serviceName(),
                    payloadJson
            );
        }

        return ResponseEntity.accepted().body(IngestionResponse.accepted(trackingId, batch.size()));
    }
}
