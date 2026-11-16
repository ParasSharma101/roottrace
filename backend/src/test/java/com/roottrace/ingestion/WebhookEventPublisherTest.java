package com.roottrace.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.repository.WorkspaceRepository;
import com.roottrace.infrastructure.kafka.EventPublisher;
import com.roottrace.ingestion.service.IngestionEventCodec;
import com.roottrace.ingestion.service.WebhookEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WebhookEventPublisherTest {
    @Test
    void publicationFailureIsUnavailableRatherThanAccepted() throws Exception {
        EventPublisher publisher = mock(EventPublisher.class);
        var codec = new IngestionEventCodec(new ObjectMapper(), mock(WorkspaceRepository.class));
        var webhooks = new WebhookEventPublisher(publisher, codec);
        UUID workspace = UUID.randomUUID();
        when(publisher.publish(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        assertThatThrownBy(() -> webhooks.publish("raw-logs", "LOG", workspace, "shared", Map.of("message", "test")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(publisher).publish(eq("raw-logs"), eq(workspace + ":shared"), contains("\"workspaceId\":\"" + workspace + "\""));
    }
}
