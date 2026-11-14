package com.roottrace.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String RAW_LOGS_TOPIC = "raw-logs";
    public static final String RAW_LOGS_DLQ_TOPIC = "raw-logs-dlq";
    public static final String INCOMING_ALERTS_TOPIC = "incoming-alerts";
    public static final String INCOMING_ALERTS_DLQ_TOPIC = "incoming-alerts-dlq";

    @Bean
    public NewTopic rawLogsTopic() {
        return TopicBuilder.name(RAW_LOGS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rawLogsDlqTopic() {
        return TopicBuilder.name(RAW_LOGS_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic incomingAlertsTopic() {
        return TopicBuilder.name(INCOMING_ALERTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic incomingAlertsDlqTopic() {
        return TopicBuilder.name(INCOMING_ALERTS_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
