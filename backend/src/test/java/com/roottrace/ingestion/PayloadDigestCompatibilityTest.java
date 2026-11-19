package com.roottrace.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.ingestion.dto.RawLogRequest;
import com.roottrace.ingestion.service.IngestionSubmissionService.Event;
import com.roottrace.ingestion.service.PayloadDigest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PayloadDigestCompatibilityTest {
    @Test
    void persistedIdempotencyIdentityRetainsLegacyJsonShape() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(Jackson2AutoConfiguration.class))
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    var log = new RawLogRequest("billing", Instant.parse("2026-11-19T00:00:00Z"), "INFO", "café", null,
                            Map.of("ratio", 1.5, "attempt", 7));
                    var events = List.of(new Event("raw-logs", "LOG", "billing", log));
                    // Persisted request hashes must survive the framework migration, including date/null/Unicode handling.
                    String legacyCanonical = "[{\"payload\":{\"logLevel\":\"INFO\",\"message\":\"café\",\"metadata\":{\"attempt\":7,\"ratio\":1.5},\"serviceName\":\"billing\",\"stackTrace\":null,\"timestamp\":\"2026-11-19T00:00:00Z\"},\"service\":\"billing\",\"topic\":\"raw-logs\",\"type\":\"LOG\"}]";
                    assertThat(PayloadDigest.canonicalHash(mapper, events)).isEqualTo(PayloadDigest.hash(legacyCanonical));
                });
    }
}
