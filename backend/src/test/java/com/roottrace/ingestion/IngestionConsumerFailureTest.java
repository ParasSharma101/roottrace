package com.roottrace.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.Workspace;
import com.roottrace.domain.repository.LogEventRepository;
import com.roottrace.infrastructure.kafka.EventPublisher;
import com.roottrace.infrastructure.kafka.KafkaTopicConfig;
import com.roottrace.infrastructure.kafka.consumers.LogEventConsumer;
import com.roottrace.ingestion.service.IngestionEventCodec;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IngestionConsumerFailureTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final LogEventRepository logs = mock(LogEventRepository.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final IngestionEventCodec codec = mock(IngestionEventCodec.class);
    private final com.roottrace.ingestion.service.IngestionInbox inbox = mock(com.roottrace.ingestion.service.IngestionInbox.class);
    private final LogEventConsumer consumer = new LogEventConsumer(logs, publisher, mapper, codec, inbox);
    private final ConsumerRecord<String, String> record = new ConsumerRecord<>(KafkaTopicConfig.RAW_LOGS_TOPIC, 0, 0, "key", "payload");

    @Test
    void persistenceFailurePropagatesForKafkaRetry() throws Exception {
        when(codec.decode("payload", "LOG")).thenReturn(new IngestionEventCodec.Decoded(Workspace.LEGACY_WORKSPACE_ID, null,
                mapper.readTree("{\"serviceName\":\"shared\",\"timestamp\":\"2026-11-17T00:00:00Z\",\"logLevel\":\"ERROR\",\"message\":\"test\"}")));
        when(inbox.firstDelivery(any(), anyString(), any())).thenReturn(true);
        when(logs.saveAll(anyList())).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        assertThatThrownBy(() -> consumer.consumeLogBatch(List.of(record))).isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void invalidEnvelopeRequiresSuccessfulDeadLetterPublication() throws Exception {
        when(codec.decode("payload", "LOG")).thenThrow(new IllegalArgumentException("unknown workspace"));
        when(publisher.publish(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("DLQ unavailable")));
        assertThatThrownBy(() -> consumer.consumeLogBatch(List.of(record))).isInstanceOf(java.util.concurrent.CompletionException.class);
        verifyNoInteractions(logs);
        when(publisher.publish(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        consumer.consumeLogBatch(List.of(record));
        verify(publisher, times(2)).publish(KafkaTopicConfig.RAW_LOGS_DLQ_TOPIC, "key", "payload");
        verifyNoInteractions(logs);
    }
}
