package com.roottrace.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.correlation.model.IncidentContextBundle;
import com.roottrace.correlation.service.IncidentContextCacheService;
import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.model.SeverityLevel;
import com.roottrace.domain.repository.IncidentRepository;
import com.roottrace.infrastructure.github.GitHubCommitPRMetadata;
import com.roottrace.infrastructure.github.GitHubService;
import com.roottrace.infrastructure.kafka.EventPublisher;
import com.roottrace.infrastructure.kafka.KafkaTopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GitHubAndRedisContextIntegrationTest extends com.roottrace.support.IntegrationTestSupport {

    @Autowired private GitHubService gitHubService;
    @Autowired private IncidentContextCacheService incidentContextCacheService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private EventPublisher eventPublisher;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("GitHubService should cache PR metadata in Redis with 10-minute TTL")
    void testGitHubServiceRedisCaching() {
        String serviceName = "payment-gateway";
        String commitSha = "abc123456789def";
        String cacheKey = "github:prs:" + serviceName + ":" + commitSha;

        // Clean any existing key
        redisTemplate.delete(cacheKey);

        // 1. First fetch (Cache MISS -> loads fallback/source and stores in Redis)
        GitHubCommitPRMetadata firstCall = gitHubService.fetchPRForCommit(serviceName, "org", "repo", commitSha);
        assertThat(firstCall).isNotNull();
        assertThat(firstCall.commitHash()).isEqualTo(commitSha);

        // Verify key exists in Redis
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedJson).isNotNull().contains(commitSha);

        Long expireSeconds = redisTemplate.getExpire(cacheKey);
        assertThat(expireSeconds).isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(600L);

        // 2. Second fetch (Cache HIT from Redis)
        GitHubCommitPRMetadata secondCall = gitHubService.fetchPRForCommit(serviceName, "org", "repo", commitSha);
        assertThat(secondCall).isEqualTo(firstCall);

        // Cleanup
        redisTemplate.delete(cacheKey);
    }

    @Test
    @DisplayName("IncidentContextCacheService should store, retrieve, and evict context bundles from Redis")
    void testIncidentContextCacheService() {
        UUID incidentId = UUID.randomUUID();
        Instant now = Instant.now();

        IncidentContextBundle bundle = new IncidentContextBundle(
                incidentId,
                "[Alert] order-service: DBTimeout",
                SeverityLevel.SEV1,
                IncidentStatus.OPEN,
                "order-service",
                now,
                now.minusSeconds(1800),
                now.plusSeconds(300),
                List.of(),
                List.of(),
                List.of(),
                0,
                now
        );

        // 1. Cache bundle
        incidentContextCacheService.cacheContextBundle(bundle);

        // 2. Retrieve bundle
        Optional<IncidentContextBundle> retrieved = incidentContextCacheService.getCachedContextBundle(incidentId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().incidentId()).isEqualTo(incidentId);
        assertThat(retrieved.get().serviceName()).isEqualTo("order-service");
        assertThat(retrieved.get().severityLevel()).isEqualTo(SeverityLevel.SEV1);

        // 3. Evict bundle
        incidentContextCacheService.evictContextBundle(incidentId);
        Optional<IncidentContextBundle> afterEviction = incidentContextCacheService.getCachedContextBundle(incidentId);
        assertThat(afterEviction).isEmpty();
    }

    @Test
    @DisplayName("End-to-end: Ingesting an alert should trigger incident creation, correlation, and Redis context caching")
    void testEndToEndAlertToRedisContextPipeline() throws Exception {
        String serviceName = "shipping-service-" + UUID.randomUUID();
        Instant now = Instant.now();

        Map<String, Object> alertPayload = Map.of(
                "source", "Datadog",
                "serviceName", serviceName,
                "alertType", "QueueBacklogHigh",
                "severity", "SEV2",
                "triggeredAt", now.toString(),
                "rawPayload", Map.of("backlog_size", 15000)
        );

        eventPublisher.publish(
                KafkaTopicConfig.INCOMING_ALERTS_TOPIC,
                serviceName,
                objectMapper.writeValueAsString(alertPayload)
        );

        // Wait up to 10s for the alert consumer to create the incident and the event listener to cache the bundle in Redis
        long deadline = System.currentTimeMillis() + 10000;
        UUID createdIncidentId = null;

        while (System.currentTimeMillis() < deadline) {
            var incidents = incidentRepository.findActiveIncidentsForService(
                    com.roottrace.security.AuthDataSeeder.DEFAULT_WORKSPACE_ID,
                    List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING),
                    serviceName,
                    now.minusSeconds(60)
            );
            if (!incidents.isEmpty()) {
                createdIncidentId = incidents.get(0).getId();
                // Check if context bundle is in Redis
                if (incidentContextCacheService.getCachedContextBundle(createdIncidentId).isPresent()) {
                    break;
                }
            }
            Thread.sleep(300);
        }

        assertThat(createdIncidentId).isNotNull();
        Optional<IncidentContextBundle> cachedBundle = incidentContextCacheService.getCachedContextBundle(createdIncidentId);
        assertThat(cachedBundle).isPresent();
        assertThat(cachedBundle.get().serviceName()).isEqualTo(serviceName);
        assertThat(cachedBundle.get().incidentTitle()).contains("QueueBacklogHigh");
    }
}
