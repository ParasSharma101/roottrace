package com.roottrace.ingestion.controller;

import jakarta.validation.Validator;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.roottrace.ingestion.service.IngestionSubmissionService;
import com.roottrace.ingestion.service.IngestionSubmissionService.Event;
import com.roottrace.security.IngestionIdentity;
import org.springframework.security.core.Authentication;
import com.roottrace.infrastructure.kafka.KafkaTopicConfig;
import com.roottrace.ingestion.dto.IngestionResponse;
import com.roottrace.ingestion.dto.RawLogRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks/logs")
@CrossOrigin(origins = "*")
public class LogWebhookController {

    private final IngestionSubmissionService submissions;
    private final Validator validator;

    public LogWebhookController(IngestionSubmissionService submissions, Validator validator) {
        this.submissions = submissions;
        this.validator = validator;
    }

    @PostMapping
    public ResponseEntity<IngestionResponse> ingestSingleLog(@RequestBody @Valid RawLogRequest request, Authentication authentication, @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey) throws JsonProcessingException {
        var response = submissions.submit(IngestionIdentity.requireWorkspace(authentication), "LOG", idempotencyKey,
                List.of(new Event(KafkaTopicConfig.RAW_LOGS_TOPIC, "LOG", request.serviceName(), request)));
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<IngestionResponse> ingestBatchLogs(@RequestBody @Valid List<@Valid RawLogRequest> batch, Authentication authentication, @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey) throws JsonProcessingException {

        // Validate the entire batch before the first publication.
        if (batch.isEmpty() || batch.size() > 500 || batch.stream().anyMatch(item -> item == null || !validator.validate(item).isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid log batch (1-500 valid logs required)");
        }
        var events = batch.stream().map(item -> new Event(KafkaTopicConfig.RAW_LOGS_TOPIC, "LOG", item.serviceName(), item)).toList();
        return ResponseEntity.accepted().body(submissions.submit(IngestionIdentity.requireWorkspace(authentication), "LOG_BATCH", idempotencyKey, events));
    }
}
