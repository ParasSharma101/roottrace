package com.roottrace.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.correlation.service.CorrelationService;
import com.roottrace.correlation.service.IncidentContextCacheService;
import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import com.roottrace.infrastructure.kafka.EventPublisher;
import com.roottrace.infrastructure.kafka.KafkaTopicConfig;
import com.roottrace.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TenantIngestionIntegrationTest extends IntegrationTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private LogEventRepository logs;
    @Autowired private IncidentRepository incidents;
    @Autowired private AlertRepository alerts;
    @Autowired private CorrelationService correlation;
    @Autowired private IncidentContextCacheService cache;
    @Autowired private EventPublisher publisher;

    @Test
    void namedKeysPropagateOwnershipThroughKafkaAndIsolateEvidenceAndDeduplication() throws Exception {
        Account a = account();
        Account b = account();
        Key keyA = key(a);
        Key keyB = key(b);
        String service = "tenant-pipeline-" + UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> logA = log(service, now, "ONLY_A");
        logA.put("workspaceId", b.workspace().toString());
        logA.put("eventVersion", 999); // User fields cannot replace the outer server envelope.
        logA.put("metadata", Map.of("workspaceId", b.workspace().toString()));
        send("/logs", keyA.secret(), logA);
        send("/logs/batch", keyB.secret(), List.of(log(service, now, "ONLY_B"), log(service, now, "ONLY_B_SECOND")));
        await(() -> logs(a.workspace(), service, now).size() == 1 && logs(b.workspace(), service, now).size() == 2);

        Map<String, Object> alert = alert(service, now);
        alert.put("workspaceId", b.workspace().toString());
        alert.put("details", Map.of("workspaceId", b.workspace().toString()));
        send("/alerts/monitoring", keyA.secret(), alert);
        send("/alerts/monitoring", keyB.secret(), alert);
        await(() -> active(a.workspace(), service, now).size() == 1 && active(b.workspace(), service, now).size() == 1);
        Incident own = active(a.workspace(), service, now).getFirst();
        Incident other = active(b.workspace(), service, now).getFirst();
        assertThat(own.getId()).isNotEqualTo(other.getId());
        send("/alerts/monitoring", keyA.secret(), alert);
        await(() -> alerts.findByIncidentIdOrderByTriggeredAtDesc(own.getId()).size() == 2);
        assertThat(active(a.workspace(), service, now)).hasSize(1);
        assertThat(alerts.findByIncidentIdOrderByTriggeredAtDesc(other.getId())).hasSize(1);
        assertThat(correlation.buildContextBundleAsJson(own.getId())).contains("ONLY_A").doesNotContain("ONLY_B");
        assertThat(correlation.buildContextBundleAsJson(other.getId())).contains("ONLY_B").doesNotContain("ONLY_A");
        assertThat(cache.getCachedContextBundle(a.workspace(), own.getId())).isPresent();
        assertThat(cache.getCachedContextBundle(b.workspace(), own.getId())).isEmpty();
        assertThat(incidents.findById(own.getId()).orElseThrow().getSummary()).isNotBlank();

        // The shared compatibility credential remains preservation-only.
        send("/logs", "test-only-ingestion-key-at-least-32-bytes-long", log(service, now, "LEGACY_ONLY"));
        Map<String, Object> rawLog = log(service, now, "LEGACY_RAW_ONLY");
        rawLog.put("workspaceId", a.workspace().toString());
        publisher.publish(KafkaTopicConfig.RAW_LOGS_TOPIC, service, mapper.writeValueAsString(rawLog)).get();
        Map<String, Object> raw = new HashMap<>(alert);
        raw.put("source", "historical");
        publisher.publish(KafkaTopicConfig.INCOMING_ALERTS_TOPIC, service, mapper.writeValueAsString(raw)).get();
        await(() -> active(Workspace.LEGACY_WORKSPACE_ID, service, now).size() == 1
                && logs(Workspace.LEGACY_WORKSPACE_ID, service, now).size() == 2);
        assertThat(alerts.findByIncidentIdOrderByTriggeredAtDesc(other.getId())).hasSize(1);
        assertThat(correlation.buildContextBundleAsJson(own.getId())).doesNotContain("LEGACY_ONLY", "LEGACY_RAW_ONLY");
    }

    @Test
    void revocationAndCredentialBoundariesApplyOnEveryRequest() throws Exception {
        Account a = account();
        Key revoked = key(a);
        Key valid = key(a);
        Map<String, Object> log = log("credentials-" + UUID.randomUUID(), Instant.now(), "valid");
        mvc.perform(delete("/api/v1/workspace/ingestion-keys/" + revoked.id()).header("Authorization", a.token()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/webhooks/logs").header("X-RootTrace-Key", revoked.secret())
                .contentType("application/json").content(mapper.writeValueAsString(log)))
                .andExpect(status().isUnauthorized());
        send("/logs", valid.secret(), log);
        mvc.perform(get("/api/v1/incidents").header("X-RootTrace-Key", valid.secret()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/webhooks/logs").header("X-RootTrace-Key", valid.secret())
                .header("Authorization", a.token()).contentType("application/json").content(mapper.writeValueAsString(log)))
                .andExpect(status().isBadRequest());
        // Selected ADMIN sessions retain their existing webhook ability with explicit ownership.
        mvc.perform(post("/api/v1/webhooks/logs").header("Authorization", a.token())
                .contentType("application/json").content(mapper.writeValueAsString(log)))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/webhooks/logs/batch").header("X-RootTrace-Key", valid.secret())
                .contentType("application/json").content("[{\"message\":\"invalid\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void maximumLengthAlertNamesFitPersistenceWithoutLosingServiceIdentity() throws Exception {
        Account account = account();
        Key key = key(account);
        String service = UUID.randomUUID() + "s".repeat(92);
        Instant now = Instant.now();
        Map<String, Object> payload = alert(service, now);
        payload.put("alertType", "f".repeat(128));
        send("/alerts/monitoring", key.secret(), payload);
        await(() -> active(account.workspace(), service, now).size() == 1);
        Incident incident = active(account.workspace(), service, now).getFirst();
        assertThat(incident.getTitle()).hasSize(255);
        assertThat(alerts.findByIncidentIdOrderByTriggeredAtDesc(incident.getId()).getFirst().getServiceName()).isEqualTo(service);
    }

    private Account account() throws Exception {
        String unique = UUID.randomUUID().toString();
        String json = mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content(mapper.writeValueAsString(Map.of(
                "fullName", "Pipeline Test", "email", unique + "@example.com", "password", "test-password", "workspaceName", unique))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode body = mapper.readTree(json);
        return new Account(UUID.fromString(body.path("workspaceId").asText()), "Bearer " + body.path("token").asText());
    }
    private Key key(Account account) throws Exception {
        String json = mvc.perform(post("/api/v1/workspace/ingestion-keys").header("Authorization", account.token())
                .contentType("application/json").content("{\"name\":\"Monitoring\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode body = mapper.readTree(json);
        return new Key(body.path("key").path("id").asText(), body.path("secret").asText());
    }
    private void send(String path, String key, Object payload) throws Exception {
        mvc.perform(post("/api/v1/webhooks" + path).header("X-RootTrace-Key", key)
                .contentType("application/json").content(mapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted());
    }
    private Map<String, Object> log(String service, Instant now, String message) {
        return new HashMap<>(Map.of("serviceName", service, "timestamp", now.toString(), "logLevel", "ERROR", "message", message));
    }
    private Map<String, Object> alert(String service, Instant now) {
        return new HashMap<>(Map.of("serviceName", service, "triggeredAt", now.toString(), "alertType", "Failure", "severity", "SEV1"));
    }
    private List<LogEvent> logs(UUID workspace, String service, Instant now) {
        return logs.findByWorkspaceIdAndServiceNameAndTimestampBetweenOrderByTimestampDesc(workspace, service, now.minusSeconds(60), now.plusSeconds(60));
    }
    private List<Incident> active(UUID workspace, String service, Instant now) {
        return incidents.findActiveIncidentsForService(workspace, List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING), service, now.minusSeconds(60));
    }
    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) { tickBackgroundWork(); Thread.sleep(100); }
        assertThat(condition.getAsBoolean()).as("asynchronous Kafka processing completed").isTrue();
    }
    private record Account(UUID workspace, String token) {}
    private record Key(String id, String secret) {}
}
