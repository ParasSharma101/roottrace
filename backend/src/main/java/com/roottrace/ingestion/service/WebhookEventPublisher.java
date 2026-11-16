package com.roottrace.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.roottrace.infrastructure.kafka.EventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class WebhookEventPublisher {
    private final EventPublisher publisher;
    private final IngestionEventCodec codec;
    public WebhookEventPublisher(EventPublisher publisher, IngestionEventCodec codec) {
        this.publisher = publisher;
        this.codec = codec;
    }

    public void publish(String topic, String type, UUID workspace, String service, Object payload) throws JsonProcessingException {
        String json = codec.encode(workspace, type, payload);
        try {
            publisher.publish(topic, workspace + ":" + service, json).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ingestion publication interrupted");
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ingestion publication not confirmed; retry may duplicate events");
        }
    }
}
