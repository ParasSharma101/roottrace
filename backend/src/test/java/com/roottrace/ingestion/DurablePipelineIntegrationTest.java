package com.roottrace.ingestion;

import com.fasterxml.jackson.databind.*;
import com.roottrace.ai.dto.*;
import com.roottrace.ai.service.AiInvestigationClient;
import com.roottrace.domain.model.*;
import com.roottrace.domain.repository.*;
import com.roottrace.infrastructure.jobs.*;
import com.roottrace.infrastructure.kafka.*;
import com.roottrace.infrastructure.kafka.consumers.*;
import com.roottrace.ingestion.service.IngestionEventCodec;
import com.roottrace.security.JwtTokenProvider;
import com.roottrace.support.IntegrationTestSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DurablePipelineIntegrationTest extends IntegrationTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkspaceRepository workspaces;
    @Autowired private UserRepository users;
    @Autowired private WorkspaceMemberRepository members;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private IncidentRepository incidents;
    @Autowired private AlertRepository alerts;
    @Autowired private HypothesisRepository hypotheses;
    @Autowired private AlertEventConsumer alertConsumer;
    @Autowired private LogEventConsumer logConsumer;
    @Autowired private IngestionEventCodec codec;
    @SpyBean private DurableTaskStore tasks;
    @SpyBean private EventPublisher publisher;
    @MockBean private AiInvestigationClient provider;

    @BeforeEach
    void defaultProvider() {
        when(provider.investigate(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).as("provider call holds no database transaction").isFalse();
            return result("default result", "0.80");
        });
    }

    @Test
    void concurrentHttpRetriesAreAtomicScopedAndRejectConflictingPayloads() throws Exception {
        Account a = account();
        Account b = account();
        String key = UUID.randomUUID().toString();
        Map<String,Object> payload = log("http-" + key, "same");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> submit(a, key, payload));
            var second = pool.submit(() -> submit(a, key, payload));
            JsonNode one = mapper.readTree(first.get(15, TimeUnit.SECONDS));
            JsonNode two = mapper.readTree(second.get(15, TimeUnit.SECONDS));
            assertThat(one).isEqualTo(two);
            UUID id = UUID.fromString(one.path("trackingId").asText());
            assertThat(count("select count(*) from durable_tasks where submission_id=?", id)).isEqualTo(1);
            // Map insertion order does not change request identity.
            assertThat(mapper.readTree(submit(a, key, new TreeMap<>(payload)))).isEqualTo(one);
            mvc.perform(post("/api/v1/webhooks/logs").header("Authorization", a.token()).header("Idempotency-Key", key)
                    .contentType("application/json").content(mapper.writeValueAsString(log("http-" + key, "changed"))))
                    .andExpect(status().isConflict());
            assertThat(mapper.readTree(submit(b, key, payload)).path("trackingId").asText()).isNotEqualTo(id.toString());
            mvc.perform(get("/api/v1/webhooks/requests/" + id).header("Authorization", b.token())).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/webhooks/requests/" + id).header("Authorization", a.token()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        }
        verify(publisher, never()).publish(eq(KafkaTopicConfig.RAW_LOGS_TOPIC), eq(a.workspace() + ":http-" + key), anyString());

        AtomicInteger enqueues = new AtomicInteger();
        doAnswer(call -> {
            if (enqueues.incrementAndGet()==2) throw new DataAccessResourceFailureException("simulated unavailable");
            return call.callRealMethod();
        }).when(taskSpy()).enqueuePublish(any(), eq(a.workspace()), any(), anyString(), anyString(), anyString());
        int before = count("select count(*) from ingestion_submissions where workspace_id=?", a.workspace());
        mvc.perform(post("/api/v1/webhooks/logs/batch").header("Authorization", a.token()).header("Idempotency-Key", "rollback")
                .contentType("application/json").content(mapper.writeValueAsString(List.of(payload, payload))))
                .andExpect(status().isServiceUnavailable());
        assertThat(count("select count(*) from ingestion_submissions where workspace_id=?", a.workspace())).isEqualTo(before);
    }

    @Test
    void brokerOutageAndCrashAfterPublishDoNotLoseOrDuplicateEvidence() throws Exception {
        Account owner = account();
        String service = "outbox-" + UUID.randomUUID();
        JsonNode accepted = mapper.readTree(submit(owner, "outage", log(service, "durable")));
        UUID submission = UUID.fromString(accepted.path("trackingId").asText());
        UUID task = jdbc.queryForObject("select id from durable_tasks where submission_id=?", UUID.class, submission);
        due(task);
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("offline"))).when(publisher)
                .publish(eq(KafkaTopicConfig.RAW_LOGS_TOPIC), eq(owner.workspace()+":"+service), anyString());
        backgroundWorker.publishOne();
        assertThat(state(task)).isEqualTo("PENDING");
        assertThat(count("select attempts from durable_tasks where id=?", task)).isEqualTo(1);
        doCallRealMethod().when(publisher).publish(eq(KafkaTopicConfig.RAW_LOGS_TOPIC), eq(owner.workspace()+":"+service), anyString());
        due(task);
        backgroundWorker.publishOne();
        await(() -> count("select count(*) from log_events where workspace_id=? and service_name=?", owner.workspace(), service)==1);
        // The broker accepted the send, but the process died before recording completion.
        jdbc.update("update durable_tasks set status='RUNNING',claim_token=?,lease_until=now()-interval '1 second' where id=?", UUID.randomUUID(), task);
        backgroundWorker.publishOne();
        assertThat(state(task)).isEqualTo("SUCCEEDED");
        assertThat(count("select attempts from durable_tasks where id=?", task)).isEqualTo(3);
        assertThat(count("select count(*) from ingestion_receipts where workspace_id=? and event_id=?", owner.workspace(), task)).isEqualTo(1);
        assertThat(count("select count(*) from log_events where workspace_id=? and service_name=?", owner.workspace(), service)).isEqualTo(1);
        mvc.perform(get("/api/v1/webhooks/requests/" + submission).header("Authorization", owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void receiptIncidentAlertAndJobRollBackTogetherAndReplayOnlyOnce() throws Exception {
        Account owner = account();
        UUID event = UUID.randomUUID();
        String service = "inbox-" + event;
        var record = alertRecord(owner, event, service);
        doThrow(new DataAccessResourceFailureException("queue unavailable")).when(taskSpy()).enqueueInvestigation(eq(owner.workspace()), any());
        assertThatThrownBy(() -> alertConsumer.consumeAlert(record)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(count("select count(*) from ingestion_receipts where workspace_id=? and event_id=?", owner.workspace(), event)).isZero();
        assertThat(count("select count(*) from incidents where workspace_id=?", owner.workspace())).isZero();
        doCallRealMethod().when(taskSpy()).enqueueInvestigation(eq(owner.workspace()), any());
        alertConsumer.consumeAlert(record);
        alertConsumer.consumeAlert(new ConsumerRecord<>(record.topic(), 1, 50, record.key(), record.value()));
        Incident incident = incidents.findByWorkspaceIdOrderByStartTimeDesc(owner.workspace()).getFirst();
        assertThat(alerts.findByIncidentIdOrderByTriggeredAtDesc(incident.getId())).hasSize(1);
        assertThat(count("select count(*) from durable_tasks where incident_id=?", incident.getId())).isEqualTo(1);
        assertThat(state(job(incident))).isEqualTo("PENDING");
        verifyNoInteractions(provider);

        UUID logId = UUID.randomUUID();
        String json = codec.encode(owner.workspace(), "LOG", logId, log(service, "once"));
        logConsumer.consumeLogBatch(List.of(new ConsumerRecord<>("raw-logs",0,1,"key",json), new ConsumerRecord<>("raw-logs",0,2,"key",json)));
        assertThat(count("select count(*) from log_events where workspace_id=? and service_name=?", owner.workspace(), service)).isEqualTo(1);
    }

    @Test
    void expiredWorkerCannotOverwriteReplacementResults() throws Exception {
        Account owner = account();
        Incident incident = queuedIncident(owner);
        UUID job = job(incident);
        due(job);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(provider.investigate(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (calls.incrementAndGet()==1) { started.countDown(); assertThat(release.await(15,TimeUnit.SECONDS)).isTrue(); return result("stale result", "0.80"); }
            return result("replacement result", "0.80");
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?> old = pool.submit(() -> backgroundWorker.investigateOne());
            try {
                assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
                jdbc.update("update durable_tasks set lease_until=now()-interval '1 second' where id=?", job);
                backgroundWorker.investigateOne();
                assertThat(state(job)).isEqualTo("SUCCEEDED");
            } finally { release.countDown(); }
            old.get(10,TimeUnit.SECONDS);
        }
        assertThat(incidents.findById(incident.getId()).orElseThrow().getSummary()).isEqualTo("replacement result");
        assertThat(hypotheses.findByIncidentIdOrderByConfidenceScoreDesc(incident.getId())).hasSize(1);
        assertThat(count("select attempts from durable_tasks where id=?", job)).isEqualTo(2);
    }

    @Test
    void humanResolutionWhileProviderRunsIsPreserved() throws Exception {
        Account owner = account();
        Incident incident = queuedIncident(owner);
        due(job(incident));
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        when(provider.investigate(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            started.countDown(); assertThat(release.await(15,TimeUnit.SECONDS)).isTrue(); return result("late result", "0.80");
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?> running = pool.submit(() -> backgroundWorker.investigateOne());
            try {
                assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
                mvc.perform(patch("/api/v1/incidents/"+incident.getId()+"/status").header("Authorization",owner.token())
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                        .andExpect(status().isOk());
            } finally { release.countDown(); }
            running.get(10,TimeUnit.SECONDS);
        }
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(hypotheses.findByIncidentIdOrderByConfidenceScoreDesc(incident.getId())).isEmpty();
        assertThat(state(job(incident))).isEqualTo("SUCCEEDED");
    }

    @Test
    void failedResultWriteRollsBackEffectsAndStopsAfterFiveAttempts() throws Exception {
        Account owner = account();
        Incident incident = queuedIncident(owner);
        UUID job = job(incident);
        when(provider.investigate(any())).thenReturn(result("must never commit", "1.50"));
        for (int attempt=1; attempt<=5; attempt++) {
            due(job);
            backgroundWorker.investigateOne();
            assertThat(count("select attempts from durable_tasks where id=?",job)).isEqualTo(attempt);
            assertThat(state(job)).isEqualTo(attempt==5 ? "FAILED" : "PENDING");
            assertThat(incidents.findById(incident.getId()).orElseThrow().getSummary()).isNull();
            assertThat(hypotheses.findByIncidentIdOrderByConfidenceScoreDesc(incident.getId())).isEmpty();
        }
        mvc.perform(get("/api/v1/incidents/"+incident.getId()+"/investigation").header("Authorization",owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.attempts").value(5));
        mvc.perform(get("/api/v1/incidents/"+incident.getId()+"/investigation").header("Authorization",account().token()))
                .andExpect(status().isNotFound());
    }

    private DurableTaskStore taskSpy() { return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(tasks); }
    private Account account() {
        String id=UUID.randomUUID().toString();
        User user=users.save(new User(id+"@example.com","unused","Worker Test"));
        Workspace workspace=workspaces.save(new Workspace("Worker",id,"retired_"+id));
        members.save(new WorkspaceMember(workspace,user,"ROLE_ADMIN"));
        return new Account(workspace.getId(),"Bearer "+tokens.generateToken(user.getId(),workspace.getId()));
    }
    private String submit(Account owner, String key, Map<String,Object> payload) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/logs").header("Authorization",owner.token()).header("Idempotency-Key",key)
                .contentType("application/json").content(mapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    }
    private Map<String,Object> log(String service,String message) { return Map.of("serviceName",service,"timestamp","2026-11-17T00:00:00Z","logLevel","ERROR","message",message); }
    private ConsumerRecord<String,String> alertRecord(Account owner,UUID event,String service) throws Exception {
        String json=codec.encode(owner.workspace(),"ALERT",event,Map.of("serviceName",service,"alertType","Failure","source","test","triggeredAt",Instant.now().toString()));
        return new ConsumerRecord<>(KafkaTopicConfig.INCOMING_ALERTS_TOPIC,0,0,owner.workspace()+":"+service,json);
    }
    private Incident queuedIncident(Account owner) throws Exception {
        alertConsumer.consumeAlert(alertRecord(owner,UUID.randomUUID(),"worker-"+UUID.randomUUID()));
        return incidents.findByWorkspaceIdOrderByStartTimeDesc(owner.workspace()).getFirst();
    }
    private UUID job(Incident incident) { return jdbc.queryForObject("select id from durable_tasks where incident_id=?",UUID.class,incident.getId()); }
    private void due(UUID task) { jdbc.update("update durable_tasks set available_at='1970-01-01T00:00:00Z' where id=?",task); }
    private String state(UUID task) { return jdbc.queryForObject("select status from durable_tasks where id=?",String.class,task); }
    private int count(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }
    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline=System.currentTimeMillis()+10000;
        while (!condition.getAsBoolean() && System.currentTimeMillis()<deadline) Thread.sleep(50);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    private AiInvestigationResult result(String summary,String confidence) {
        return new AiInvestigationResult(summary,List.of(),List.of(new AiHypothesisItem("hypothesis",new BigDecimal(confidence),List.of())));
    }
    private record Account(UUID workspace,String token) {}
}
