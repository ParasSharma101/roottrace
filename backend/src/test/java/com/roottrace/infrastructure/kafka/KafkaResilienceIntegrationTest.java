package com.roottrace.infrastructure.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KafkaResilienceIntegrationTest extends com.roottrace.support.IntegrationTestSupport {

    @Autowired private KafkaAdmin kafkaAdmin;
    @Autowired private EventPublisher eventPublisher;

    @Test
    @DisplayName("Should successfully provision all topics with configured partitions")
    void testKafkaTopicsProvisioned() throws Exception {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            ListTopicsResult topicsResult = adminClient.listTopics();
            Set<String> topicNames = topicsResult.names().get(10, TimeUnit.SECONDS);

            assertThat(topicNames).contains(
                    KafkaTopicConfig.RAW_LOGS_TOPIC,
                    KafkaTopicConfig.RAW_LOGS_DLQ_TOPIC,
                    KafkaTopicConfig.INCOMING_ALERTS_TOPIC,
                    KafkaTopicConfig.INCOMING_ALERTS_DLQ_TOPIC
            );

            Map<String, TopicDescription> descriptions = adminClient
                    .describeTopics(List.of(KafkaTopicConfig.RAW_LOGS_TOPIC, KafkaTopicConfig.RAW_LOGS_DLQ_TOPIC))
                    .allTopicNames()
                    .get(10, TimeUnit.SECONDS);

            // raw-logs must have 3 partitions for horizontal service-level parallelism
            assertThat(descriptions.get(KafkaTopicConfig.RAW_LOGS_TOPIC).partitions()).hasSize(3);
            // DLQ has 1 partition
            assertThat(descriptions.get(KafkaTopicConfig.RAW_LOGS_DLQ_TOPIC).partitions()).hasSize(1);
        }
    }

    @Test
    @DisplayName("Should publish message to Kafka topic and consume it successfully")
    void testEventPublisher() throws Exception {
        String testKey = "order-service";
        String testPayload = "{\"message\": \"test-healthcheck-event\"}";

        CompletableFuture<SendResult<String, String>> future = eventPublisher.publish(
                KafkaTopicConfig.RAW_LOGS_TOPIC,
                testKey,
                testPayload
        );

        SendResult<String, String> sendResult = future.get(5, TimeUnit.SECONDS);
        assertThat(sendResult).isNotNull();
        assertThat(sendResult.getRecordMetadata().topic()).isEqualTo(KafkaTopicConfig.RAW_LOGS_TOPIC);

        // Verify message received by consumer
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verification-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(KafkaTopicConfig.RAW_LOGS_TOPIC));
            boolean received = false;
            long deadline = System.currentTimeMillis() + 10000;

            while (System.currentTimeMillis() < deadline && !received) {
                var records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (testPayload.equals(record.value())) {
                        received = true;
                        assertThat(record.key()).isEqualTo(testKey);
                        break;
                    }
                }
            }
            assertThat(received).as("Message should be consumed from Kafka topic").isTrue();
        }
    }
}
