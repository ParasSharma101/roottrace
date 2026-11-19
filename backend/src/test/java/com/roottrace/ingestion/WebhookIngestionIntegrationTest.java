package com.roottrace.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.ingestion.dto.AlertWebhookRequest;
import com.roottrace.ingestion.dto.RawLogRequest;
import com.roottrace.security.WebhookApiKeyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookIngestionIntegrationTest extends com.roottrace.support.IntegrationTestSupport {

    private static final String VALID_KEY = "test-only-ingestion-key-at-least-32-bytes-long";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/webhooks/logs - should reject unauthenticated requests with 401 Unauthorized")
    void testIngestLogsMissingApiKey() throws Exception {
        RawLogRequest request = new RawLogRequest(
                "checkout-service",
                Instant.now(),
                "ERROR",
                "Connection reset by peer",
                "java.net.SocketException...",
                Map.of("region", "us-east-1")
        );

        mockMvc.perform(post("/api/v1/webhooks/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/logs - should reject invalid X-RootTrace-Key with 401")
    void testIngestLogsInvalidApiKey() throws Exception {
        RawLogRequest request = new RawLogRequest(
                "checkout-service",
                Instant.now(),
                "ERROR",
                "Connection reset",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/webhooks/logs")
                        .header(WebhookApiKeyFilter.API_KEY_HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/logs - should accept valid single log with X-RootTrace-Key and return 202")
    void testIngestSingleLogSuccess() throws Exception {
        RawLogRequest request = new RawLogRequest(
                "checkout-service",
                Instant.now(),
                "ERROR",
                "Connection reset by peer",
                "java.net.SocketException...",
                Map.of("region", "us-east-1")
        );

        mockMvc.perform(post("/api/v1/webhooks/logs")
                        .header(WebhookApiKeyFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.trackingId").isNotEmpty())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/logs - should reject invalid log with missing fields when authenticated")
    void testIngestSingleLogValidationError() throws Exception {
        // Missing serviceName and timestamp
        String invalidJson = "{\"logLevel\": \"ERROR\", \"message\": \"test\"}";

        mockMvc.perform(post("/api/v1/webhooks/logs")
                        .header(WebhookApiKeyFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.serviceName").exists())
                .andExpect(jsonPath("$.fieldErrors.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/logs/batch - should accept batch of logs with X-RootTrace-Key and return 202")
    void testIngestBatchLogsSuccess() throws Exception {
        Instant now = Instant.now();
        List<RawLogRequest> batch = List.of(
                new RawLogRequest("auth-service", now, "INFO", "User login successful", null, null),
                new RawLogRequest("auth-service", now, "WARN", "Slow token verification", null, null)
        );

        mockMvc.perform(post("/api/v1/webhooks/logs/batch")
                        .header(WebhookApiKeyFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/alerts/{source} - should accept alert with X-RootTrace-Key and return 202")
    void testIngestAlertSuccess() throws Exception {
        AlertWebhookRequest request = new AlertWebhookRequest(
                "payment-service",
                "HighLatency",
                "SEV1",
                Instant.now(),
                Map.of("p99_latency", "5200ms", "threshold", "2000ms")
        );

        mockMvc.perform(post("/api/v1/webhooks/alerts/pagerduty")
                        .header(WebhookApiKeyFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.trackingId").isNotEmpty());
    }
}
