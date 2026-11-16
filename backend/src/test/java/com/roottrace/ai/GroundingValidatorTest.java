package com.roottrace.ai;

import com.roottrace.ai.dto.AiEvidenceCitation;
import com.roottrace.ai.dto.AiHypothesisItem;
import com.roottrace.ai.dto.AiInvestigationResult;
import com.roottrace.ai.validator.GroundingValidator;
import com.roottrace.correlation.model.*;
import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.model.LogLevel;
import com.roottrace.domain.model.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingValidatorTest {

    private final GroundingValidator validator = new GroundingValidator();

    @Test
    @DisplayName("Should preserve valid citations and strip hallucinated log and PR citations")
    void testGroundingValidation() {
        UUID validLogId = UUID.randomUUID();
        UUID validPrId = UUID.randomUUID();
        UUID fakeLogId = UUID.randomUUID();
        UUID fakePrId = UUID.randomUUID();

        // 1. Create Context Bundle with known genuine IDs
        CompressedLogSignature logSig = new CompressedLogSignature(
                "HikariPool-1 - Connection is not available",
                LogLevel.ERROR,
                50,
                Instant.now().minusSeconds(600),
                Instant.now(),
                "Connection timeout after 30000ms",
                "org.postgresql.util.PSQLException...",
                validLogId
        );

        PRSummary prSummary = new PRSummary(
                validPrId,
                42,
                "refactor(payment): update client timeout",
                "alice",
                "+ timeout = 5000ms",
                "[\"PaymentClient.java\"]",
                Instant.now().minusSeconds(1200)
        );

        DeploymentSummary deploymentSummary = new DeploymentSummary(
                UUID.randomUUID(),
                "payment-service",
                Instant.now().minusSeconds(1000),
                "v2.1.0",
                "abcdef1",
                "production",
                List.of(prSummary)
        );

        IncidentContextBundle bundle = new IncidentContextBundle(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "[Alert] payment-service: LatencyHigh",
                SeverityLevel.SEV1,
                IncidentStatus.OPEN,
                "payment-service",
                Instant.now(),
                Instant.now().minusSeconds(1800),
                Instant.now().plusSeconds(300),
                List.of(),
                List.of(deploymentSummary),
                List.of(logSig),
                50,
                Instant.now()
        );

        // 2. Create raw AI result with 1 valid citation, 1 fake log citation, and 1 fake PR citation
        AiEvidenceCitation validLogCitation = new AiEvidenceCitation("LOG", validLogId, "HikariPool timeout");
        AiEvidenceCitation validPrCitation = new AiEvidenceCitation("PR", validPrId, "timeout = 5000ms");
        AiEvidenceCitation hallucinatedLogCitation = new AiEvidenceCitation("LOG", fakeLogId, "Fabricated stack trace");
        AiEvidenceCitation hallucinatedPrCitation = new AiEvidenceCitation("PR", fakePrId, "Fabricated PR change");

        AiHypothesisItem hypothesisWithMixedEvidence = new AiHypothesisItem(
                "Connection pool starvation due to downstream timeout",
                new BigDecimal("0.95"),
                List.of(validLogCitation, validPrCitation, hallucinatedLogCitation)
        );

        AiHypothesisItem hypothesisWithOnlyHallucinatedEvidence = new AiHypothesisItem(
                "Unrelated memory leak guess",
                new BigDecimal("0.80"),
                List.of(hallucinatedPrCitation)
        );

        AiInvestigationResult rawResult = new AiInvestigationResult(
                "Service degradation caused by connection exhaustion",
                List.of("PR #42 updated client timeout"),
                List.of(hypothesisWithMixedEvidence, hypothesisWithOnlyHallucinatedEvidence)
        );

        // 3. Execute validation
        AiInvestigationResult sanitizedResult = validator.validateAndSanitize(rawResult, bundle);

        // Assertions
        assertThat(sanitizedResult.hypotheses()).hasSize(2);

        // First hypothesis: valid citations preserved, hallucinated citation stripped
        AiHypothesisItem sanitizedHypothesis1 = sanitizedResult.hypotheses().get(0);
        assertThat(sanitizedHypothesis1.evidenceCitations()).hasSize(2);
        assertThat(sanitizedHypothesis1.evidenceCitations())
                .extracting(AiEvidenceCitation::refId)
                .containsExactly(validLogId, validPrId);
        assertThat(sanitizedHypothesis1.confidenceScore()).isEqualByComparingTo("0.95");

        // Second hypothesis: all citations were hallucinated, stripped -> confidence penalized to 0.10
        AiHypothesisItem sanitizedHypothesis2 = sanitizedResult.hypotheses().get(1);
        assertThat(sanitizedHypothesis2.evidenceCitations()).isEmpty();
        assertThat(sanitizedHypothesis2.confidenceScore()).isEqualByComparingTo("0.10");
    }
}
