package com.roottrace.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.ai.dto.AiEvidenceCitation;
import com.roottrace.ai.dto.AiHypothesisItem;
import com.roottrace.ai.dto.AiInvestigationResult;
import com.roottrace.ai.prompt.SrePromptBuilder;
import com.roottrace.ai.validator.GroundingValidator;
import com.roottrace.correlation.model.CompressedLogSignature;
import com.roottrace.correlation.model.IncidentContextBundle;
import com.roottrace.correlation.model.PRSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;

@Service
public class AiInvestigationClient {

    private static final Logger log = LoggerFactory.getLogger(AiInvestigationClient.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}";

    private final SrePromptBuilder srePromptBuilder;
    private final GroundingValidator groundingValidator;
    private final ObjectMapper objectMapper;
    private final String geminiApiKey;
    private final String geminiModel;
    private final RestClient restClient;

    public AiInvestigationClient(SrePromptBuilder srePromptBuilder,
                                 GroundingValidator groundingValidator,
                                 ObjectMapper objectMapper,
                                 @Value("${gemini.api-key:}") String geminiApiKey,
                                 @Value("${gemini.model:gemini-1.5-flash}") String geminiModel) {
        this.srePromptBuilder = srePromptBuilder;
        this.groundingValidator = groundingValidator;
        this.objectMapper = objectMapper;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
        this.restClient = RestClient.builder().build();
    }

    public AiInvestigationResult investigate(IncidentContextBundle bundle) {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                log.info("Calling Google Gemini API model [{}] for incident [{}]", geminiModel, bundle.incidentId());
                return callGeminiApi(bundle);
            } catch (Exception ex) {
                log.warn("Gemini API call failed (error: {}). Falling back to heuristic SRE engine.", ex.getMessage(), ex);
            }
        } else {
            log.info("GEMINI_API_KEY is not set. Using deterministic SRE heuristic engine for incident [{}]", bundle.incidentId());
        }

        AiInvestigationResult heuristicResult = generateHeuristicInvestigation(bundle);
        return groundingValidator.validateAndSanitize(heuristicResult, bundle);
    }

    private AiInvestigationResult callGeminiApi(IncidentContextBundle bundle) throws Exception {
        String contextJson = objectMapper.writeValueAsString(bundle);
        String systemPrompt = srePromptBuilder.buildSystemPrompt();
        String userPrompt = srePromptBuilder.buildUserPrompt(contextJson);

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.2
                )
        );

        String responseJson = restClient.post()
                .uri(GEMINI_API_URL, geminiModel, geminiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(responseJson);
        String candidateText = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();

        AiInvestigationResult rawResult = objectMapper.readValue(candidateText, AiInvestigationResult.class);
        return groundingValidator.validateAndSanitize(rawResult, bundle);
    }

    public AiInvestigationResult generateHeuristicInvestigation(IncidentContextBundle bundle) {
        List<CompressedLogSignature> errorLogs = bundle.compressedLogs().stream()
                .filter(l -> l.logLevel().name().equals("ERROR") || l.logLevel().name().equals("FATAL"))
                .toList();

        List<PRSummary> allPrs = bundle.deployments().stream()
                .flatMap(d -> d.pullRequests().stream())
                .toList();

        // 1. Check Scenario A: Database Migration / Lock Contention / Connection Exhaustion
        boolean hasDbContention = errorLogs.stream().anyMatch(l ->
                l.signature().contains("PSQLException") ||
                l.signature().contains("LockAcquisitionException") ||
                l.signature().contains("HikariPool") ||
                l.sampleMessage().toLowerCase().contains("connection is not available")
        );

        Optional<PRSummary> migrationPr = allPrs.stream().filter(pr ->
                pr.title().toLowerCase().contains("migration") ||
                pr.title().toLowerCase().contains("flyway") ||
                pr.title().toLowerCase().contains("schema") ||
                pr.diffSummary().toLowerCase().contains("alter table")
        ).findFirst();

        if (hasDbContention && migrationPr.isPresent()) {
            CompressedLogSignature dbLog = errorLogs.stream().filter(l ->
                    l.signature().contains("PSQLException") ||
                    l.signature().contains("LockAcquisitionException") ||
                    l.signature().contains("HikariPool")
            ).findFirst().orElse(errorLogs.get(0));

            PRSummary pr = migrationPr.get();

            List<AiHypothesisItem> hypotheses = List.of(
                    new AiHypothesisItem(
                            "Database connection pool exhaustion caused by table-level exclusive lock contention introduced in Flyway migration PR #" + pr.prNumber() + ".",
                            new BigDecimal("0.95"),
                            List.of(
                                    new AiEvidenceCitation("PR", pr.prId(), pr.diffSummary()),
                                    new AiEvidenceCitation("LOG", dbLog.sampleLogId(), dbLog.sampleMessage())
                            )
                    ),
                    new AiHypothesisItem(
                            "High concurrent transaction volume overwhelming database connection pool limits.",
                            new BigDecimal("0.60"),
                            List.of(
                                    new AiEvidenceCitation("LOG", dbLog.sampleLogId(), dbLog.sampleMessage())
                            )
                    )
            );

            return new AiInvestigationResult(
                    "Service outage detected on " + bundle.serviceName() + " characterized by database connection pool starvation following deployment of PR #" + pr.prNumber() + ".",
                    List.of("PR #" + pr.prNumber() + " merged: " + pr.title()),
                    hypotheses
            );
        }

        // 2. Check Scenario B: Upstream Timeout / Cascading Failure
        boolean hasTimeout = errorLogs.stream().anyMatch(l ->
                l.signature().contains("SocketTimeoutException") ||
                l.signature().contains("Timeout") ||
                l.sampleMessage().toLowerCase().contains("timed out")
        );

        Optional<PRSummary> timeoutPr = allPrs.stream().filter(pr ->
                pr.title().toLowerCase().contains("timeout") ||
                pr.diffSummary().toLowerCase().contains("timeout")
        ).findFirst();

        if (hasTimeout && timeoutPr.isPresent()) {
            CompressedLogSignature timeoutLog = errorLogs.stream().filter(l ->
                    l.signature().contains("Timeout") || l.sampleMessage().toLowerCase().contains("timed out")
            ).findFirst().orElse(errorLogs.get(0));

            PRSummary pr = timeoutPr.get();

            List<AiHypothesisItem> hypotheses = List.of(
                    new AiHypothesisItem(
                            "Cascading request latency caused by increased downstream client timeout in PR #" + pr.prNumber() + " exhausting worker threads.",
                            new BigDecimal("0.92"),
                            List.of(
                                    new AiEvidenceCitation("PR", pr.prId(), pr.diffSummary()),
                                    new AiEvidenceCitation("LOG", timeoutLog.sampleLogId(), timeoutLog.sampleMessage())
                            )
                    )
            );

            return new AiInvestigationResult(
                    "Latency spike and thread starvation on " + bundle.serviceName() + " triggered by upstream timeout configuration changes in PR #" + pr.prNumber() + ".",
                    List.of("PR #" + pr.prNumber() + " modified client timeout: " + pr.diffSummary()),
                    hypotheses
            );
        }

        // 3. Generic Outage Analysis Fallback
        if (!errorLogs.isEmpty()) {
            CompressedLogSignature topError = errorLogs.get(0);
            List<AiEvidenceCitation> citations = new ArrayList<>();
            citations.add(new AiEvidenceCitation("LOG", topError.sampleLogId(), topError.sampleMessage()));

            if (!allPrs.isEmpty()) {
                citations.add(new AiEvidenceCitation("PR", allPrs.get(0).prId(), allPrs.get(0).title()));
            }

            return new AiInvestigationResult(
                    "Error spike of " + topError.count() + " occurrences observed on " + bundle.serviceName() + ".",
                    allPrs.stream().map(pr -> "PR #" + pr.prNumber() + ": " + pr.title()).toList(),
                    List.of(new AiHypothesisItem(
                            "Uncaught runtime exception: " + topError.signature(),
                            new BigDecimal("0.75"),
                            citations
                    ))
            );
        }

        return new AiInvestigationResult(
                "Incident triggered for " + bundle.serviceName() + ". No active error logs detected in time window.",
                List.of(),
                List.of()
        );
    }
}
