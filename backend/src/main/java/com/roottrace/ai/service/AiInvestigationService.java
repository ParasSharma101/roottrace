package com.roottrace.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.ai.dto.AiHypothesisItem;
import com.roottrace.ai.dto.AiInvestigationResult;
import com.roottrace.correlation.model.IncidentContextBundle;
import com.roottrace.correlation.service.CorrelationService;
import com.roottrace.correlation.service.IncidentContextCacheService;
import com.roottrace.domain.model.Hypothesis;
import com.roottrace.domain.model.HypothesisStatus;
import com.roottrace.domain.model.Incident;
import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.repository.HypothesisRepository;
import com.roottrace.domain.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiInvestigationService {

    private static final Logger log = LoggerFactory.getLogger(AiInvestigationService.class);

    private final IncidentRepository incidentRepository;
    private final HypothesisRepository hypothesisRepository;
    private final IncidentContextCacheService incidentContextCacheService;
    private final CorrelationService correlationService;
    private final AiInvestigationClient aiInvestigationClient;
    private final ObjectMapper objectMapper;

    public AiInvestigationService(IncidentRepository incidentRepository,
                                  HypothesisRepository hypothesisRepository,
                                  IncidentContextCacheService incidentContextCacheService,
                                  CorrelationService correlationService,
                                  AiInvestigationClient aiInvestigationClient,
                                  ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.hypothesisRepository = hypothesisRepository;
        this.incidentContextCacheService = incidentContextCacheService;
        this.correlationService = correlationService;
        this.aiInvestigationClient = aiInvestigationClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Hypothesis> investigateIncident(UUID incidentId) {
        log.info("Starting AI investigation workflow for incident [{}]", incidentId);

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        // 1. Get Context Bundle from Redis cache (or build from DB if cache miss)
        IncidentContextBundle bundle = incidentContextCacheService.getCachedContextBundle(incidentId)
                .orElseGet(() -> {
                    log.info("Cache miss in Redis for incident [{}]. Correlating directly from database.", incidentId);
                    IncidentContextBundle built = correlationService.buildContextBundle(incidentId);
                    incidentContextCacheService.cacheContextBundle(built);
                    return built;
                });

        // 2. Call AI investigation engine (Gemini API with heuristic fallback + grounding validation)
        AiInvestigationResult aiResult = aiInvestigationClient.investigate(bundle);

        // 3. Update Incident record
        incident.setStatus(IncidentStatus.INVESTIGATING);
        incident.setSummary(aiResult.summary());
        incidentRepository.save(incident);

        // 4. Persist grounded hypotheses
        List<Hypothesis> savedHypotheses = new ArrayList<>();
        for (AiHypothesisItem item : aiResult.hypotheses()) {
            try {
                String citationsJson = objectMapper.writeValueAsString(item.evidenceCitations());
                Hypothesis hypothesis = new Hypothesis(
                        incident,
                        item.description(),
                        item.confidenceScore(),
                        HypothesisStatus.PENDING,
                        citationsJson
                );
                savedHypotheses.add(hypothesisRepository.save(hypothesis));
            } catch (Exception ex) {
                log.error("Failed to serialize citations for hypothesis: {}", ex.getMessage());
            }
        }

        log.info("Investigation complete for incident [{}]. Persisted {} grounded hypotheses.",
                incidentId, savedHypotheses.size());

        return savedHypotheses;
    }
}
