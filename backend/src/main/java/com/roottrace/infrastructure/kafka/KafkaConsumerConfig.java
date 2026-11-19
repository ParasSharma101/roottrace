package com.roottrace.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaOperations<Object, Object> kafkaOperations) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations, (ConsumerRecord<?, ?> record, Exception ex) -> {
            String originalTopic = record.topic();
            String dlqTopic = originalTopic + "-dlq";
            log.error("Quarantining poisoned record from topic [{}] to DLQ [{}]. Partition: {}, Offset: {}, Error: {}",
                    originalTopic, dlqTopic, record.partition(), record.offset(), ex.getMessage());
            return new TopicPartition(dlqTopic, 0);
        });
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(java.time.Duration.ofSeconds(10));
        return recoverer;
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        // Retry 2 times with a 500ms pause before forwarding to DLQ
        FixedBackOff backOff = new FixedBackOff(500L, 2L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setBackOffFunction((record, failure) -> {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.springframework.dao.TransientDataAccessException
                        || cause instanceof org.springframework.dao.DataAccessResourceFailureException
                        || cause instanceof org.springframework.transaction.CannotCreateTransactionException) {
                    // A temporary database outage must not turn a valid event into a poison record.
                    return new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS);
                }
            }
            return backOff;
        });

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Retrying delivery attempt #{} for topic: {}, offset: {}. Error: {}",
                    deliveryAttempt, record.topic(), record.offset(), ex.getMessage());
        });

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }
}
