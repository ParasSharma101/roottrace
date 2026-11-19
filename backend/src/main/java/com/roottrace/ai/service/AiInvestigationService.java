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

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public AiInvestigationResult evaluate(UUID workspaceId, UUID incidentId) {
        IncidentContextBundle bundle = correlationService.buildContextBundle(incidentId);
        if (!workspaceId.equals(bundle.workspaceId())) throw new IllegalStateException("Investigation ownership mismatch");
        incidentContextCacheService.cacheContextBundle(bundle);
        return aiInvestigationClient.investigate(bundle);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<Hypothesis> applyResult(UUID workspaceId, UUID incidentId, AiInvestigationResult result) {
        Incident incident = incidentRepository.lockByIdAndWorkspaceId(incidentId, workspaceId)
                .orElseThrow(() -> new IllegalStateException("Investigation incident not found"));
        // Preserve a human resolution that happened while the provider was running.
        if (incident.getStatus() != IncidentStatus.OPEN && incident.getStatus() != IncidentStatus.INVESTIGATING) return List.of();
        List<Hypothesis> prepared = new ArrayList<>();
        for (AiHypothesisItem item : result.hypotheses()) {
            try {
                prepared.add(new Hypothesis(incident, item.description(), item.confidenceScore(), HypothesisStatus.PENDING,
                        objectMapper.writeValueAsString(item.evidenceCitations())));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Cannot serialize investigation citations", ex);
            }
        }
        incident.setStatus(IncidentStatus.INVESTIGATING);
        incident.setSummary(result.summary());
        incidentRepository.save(incident);
        return hypothesisRepository.saveAll(prepared);
    }
}
