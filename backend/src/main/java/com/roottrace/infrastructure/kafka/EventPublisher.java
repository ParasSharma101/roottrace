package com.roottrace.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, String>> publish(String topic, String key, String payload) {
        return kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish message to topic [{}] with key [{}]: {}",
                                topic, key, ex.getMessage(), ex);
                    } else {
                        log.debug("Successfully published message to topic [{}] partition [{}] offset [{}]",
                                topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
