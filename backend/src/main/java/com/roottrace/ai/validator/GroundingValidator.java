package com.roottrace.ai.validator;

import com.roottrace.ai.dto.AiEvidenceCitation;
import com.roottrace.ai.dto.AiHypothesisItem;
import com.roottrace.ai.dto.AiInvestigationResult;
import com.roottrace.correlation.model.CompressedLogSignature;
import com.roottrace.correlation.model.IncidentContextBundle;
import com.roottrace.correlation.model.PRSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GroundingValidator {

    private static final Logger log = LoggerFactory.getLogger(GroundingValidator.class);

    public AiInvestigationResult validateAndSanitize(AiInvestigationResult rawResult, IncidentContextBundle bundle) {
        if (rawResult == null) {
            return new AiInvestigationResult("No AI response provided", List.of(), List.of());
        }

        // 1. Index all genuinely existing log IDs from the retrieved time-window slice
        Set<UUID> validLogIds = bundle.compressedLogs().stream()
                .map(CompressedLogSignature::sampleLogId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. Index all genuinely existing PR IDs from the retrieved time-window slice
        Set<UUID> validPrIds = bundle.deployments().stream()
                .flatMap(d -> d.pullRequests().stream())
                .map(PRSummary::prId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<AiHypothesisItem> sanitizedHypotheses = new ArrayList<>();

        for (AiHypothesisItem hypothesis : rawResult.hypotheses()) {
            List<AiEvidenceCitation> validCitations = new ArrayList<>();

            if (hypothesis.evidenceCitations() != null) {
                for (AiEvidenceCitation citation : hypothesis.evidenceCitations()) {
                    if (citation.refId() == null) {
                        log.warn("Stripping citation with null refId: {}", citation);
                        continue;
                    }

                    String type = citation.type() != null ? citation.type().toUpperCase() : "UNKNOWN";

                    if ("LOG".equals(type)) {
                        if (validLogIds.contains(citation.refId())) {
                            validCitations.add(citation);
                        } else {
                            log.warn("HALLUCINATION DETECTED: Log refId [{}] does not exist in context bundle. Stripping citation.",
                                    citation.refId());
                        }
                    } else if ("PR".equals(type)) {
                        if (validPrIds.contains(citation.refId())) {
                            validCitations.add(citation);
                        } else {
                            log.warn("HALLUCINATION DETECTED: PR refId [{}] does not exist in context bundle. Stripping citation.",
                                    citation.refId());
                        }
                    } else {
                        log.warn("Unknown citation type [{}] with refId [{}]. Stripping.", type, citation.refId());
                    }
                }
            }

            // Adjust confidence if all citations were hallucinated/stripped
            BigDecimal adjustedConfidence = hypothesis.confidenceScore();
            if (validCitations.isEmpty()) {
                log.warn("Hypothesis '{}' has 0 valid citations after grounding check. Penalizing confidence score to 0.10",
                        hypothesis.description());
                adjustedConfidence = new BigDecimal("0.10");
            }

            sanitizedHypotheses.add(new AiHypothesisItem(
                    hypothesis.description(),
                    adjustedConfidence,
                    validCitations
            ));
        }

        return new AiInvestigationResult(
                rawResult.summary(),
                rawResult.suspiciousChanges() != null ? rawResult.suspiciousChanges() : List.of(),
                sanitizedHypotheses
        );
    }
}
