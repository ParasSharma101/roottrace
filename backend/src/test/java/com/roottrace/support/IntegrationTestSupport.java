package com.roottrace.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One disposable service set per test JVM. Do not combine this singleton lifecycle
 * with @Container: Spring can reuse contexts after a test class finishes.
 * Docker is required; never fall back to development services when unavailable.
 */
public abstract class IntegrationTestSupport {
    @org.springframework.beans.factory.annotation.Autowired
    protected com.roottrace.infrastructure.jobs.DurableTaskWorker backgroundWorker;
    protected void tickBackgroundWork() {
        for (int i=0; i<100 && backgroundWorker.publishOne(); i++) { }
        for (int i=0; i<100 && backgroundWorker.investigateOne(); i++) { }
    }

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("roottrace_test")
            .withUsername("roottrace_test")
            .withPassword("test-only-password");
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.0");

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void isolatedServices(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.username", () -> "");
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.database", () -> 0);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("roottrace.jobs.scheduling.enabled", () -> "false");
        registry.add("github.token", () -> "");
        registry.add("gemini.api-key", () -> "");
        registry.add("roottrace.security.jwt.secret", () ->
                "test-only-jwt-signing-secret-at-least-256-bits-long");
        registry.add("roottrace.security.ingestion.api-key", () -> "test-only-ingestion-key-at-least-32-bytes-long");
        registry.add("roottrace.security.break-glass.token", () -> "roottrace_emergency_breakglass_token");
    }
}
