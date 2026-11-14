package com.roottrace.correlation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.correlation.model.IncidentContextBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentContextCacheService {

    private static final Logger log = LoggerFactory.getLogger(IncidentContextCacheService.class);
    private static final Duration CONTEXT_TTL = Duration.ofHours(2);
    private static final String KEY_PREFIX = "incident:";
    private static final String KEY_SUFFIX = ":context";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IncidentContextCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void cacheContextBundle(IncidentContextBundle bundle) {
        if (bundle == null || bundle.incidentId() == null) return;

        String key = buildKey(bundle.incidentId());
        try {
            String json = objectMapper.writeValueAsString(bundle);
            redisTemplate.opsForValue().set(key, json, CONTEXT_TTL);
            log.info("Cached incident context bundle in Redis for key [{}] with TTL {}", key, CONTEXT_TTL);
        } catch (Exception ex) {
            log.error("Failed to cache incident context bundle in Redis: {}", ex.getMessage());
        }
    }

    public Optional<IncidentContextBundle> getCachedContextBundle(UUID incidentId) {
        String key = buildKey(incidentId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                IncidentContextBundle bundle = objectMapper.readValue(json, IncidentContextBundle.class);
                log.info("Redis cache HIT for incident context [{}]", key);
                return Optional.of(bundle);
            }
        } catch (Exception ex) {
            log.warn("Failed to retrieve incident context from Redis for key [{}]: {}", key, ex.getMessage());
        }
        return Optional.empty();
    }

    public void evictContextBundle(UUID incidentId) {
        String key = buildKey(incidentId);
        try {
            redisTemplate.delete(key);
            log.info("Evicted incident context from Redis for key [{}]", key);
        } catch (Exception ex) {
            log.warn("Failed to evict incident context from Redis: {}", ex.getMessage());
        }
    }

    private String buildKey(UUID incidentId) {
        return KEY_PREFIX + incidentId + KEY_SUFFIX;
    }
}
