package com.roottrace.infrastructure.kafka.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.LogEvent;
import com.roottrace.domain.model.LogLevel;
import com.roottrace.domain.repository.LogEventRepository;
import com.roottrace.infrastructure.kafka.EventPublisher;
import com.roottrace.infrastructure.kafka.KafkaTopicConfig;
import com.roottrace.ingestion.dto.RawLogRequest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class LogEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogEventConsumer.class);

    private final LogEventRepository logEventRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public LogEventConsumer(LogEventRepository logEventRepository,
                            EventPublisher eventPublisher,
                            ObjectMapper objectMapper) {
        this.logEventRepository = logEventRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.RAW_LOGS_TOPIC,
            containerFactory = "batchKafkaListenerContainerFactory",
            groupId = "roottrace-log-processor"
    )
    @Transactional
    public void consumeLogBatch(List<ConsumerRecord<String, String>> records) {
        log.info("Received batch of {} log records from Kafka topic [{}]", records.size(), KafkaTopicConfig.RAW_LOGS_TOPIC);

        List<LogEvent> eventsToPersist = new ArrayList<>();

        for (ConsumerRecord<String, String> record : records) {
            try {
                RawLogRequest request = objectMapper.readValue(record.value(), RawLogRequest.class);

                LogLevel level;
                try {
                    level = LogLevel.valueOf(request.logLevel().toUpperCase());
                } catch (IllegalArgumentException e) {
                    level = LogLevel.INFO;
                }

                String metadataJson = request.metadata() != null ? objectMapper.writeValueAsString(request.metadata()) : null;

                LogEvent event = new LogEvent(
                        request.serviceName(),
                        request.timestamp(),
                        level,
                        request.message(),
                        request.stackTrace(),
                        metadataJson
                );

                eventsToPersist.add(event);
            } catch (Exception ex) {
                log.error("Failed to parse log record at partition {} offset {}. Routing to DLQ: {}",
                        record.partition(), record.offset(), ex.getMessage());
                eventPublisher.publish(
                        KafkaTopicConfig.RAW_LOGS_DLQ_TOPIC,
                        record.key(),
                        record.value()
                );
            }
        }

        if (!eventsToPersist.isEmpty()) {
            logEventRepository.saveAll(eventsToPersist);
            log.info("Successfully persisted batch of {} log events into database", eventsToPersist.size());
        }
    }
}
