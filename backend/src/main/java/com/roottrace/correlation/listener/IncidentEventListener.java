package com.roottrace.correlation.listener;

import com.roottrace.ai.service.AiInvestigationService;
import com.roottrace.correlation.model.IncidentContextBundle;
import com.roottrace.correlation.service.CorrelationService;
import com.roottrace.correlation.service.IncidentContextCacheService;
import com.roottrace.domain.event.IncidentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class IncidentEventListener {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventListener.class);

    private final CorrelationService correlationService;
    private final IncidentContextCacheService incidentContextCacheService;
    private final AiInvestigationService aiInvestigationService;

    public IncidentEventListener(CorrelationService correlationService,
                                 IncidentContextCacheService incidentContextCacheService,
                                 AiInvestigationService aiInvestigationService) {
        this.correlationService = correlationService;
        this.incidentContextCacheService = incidentContextCacheService;
        this.aiInvestigationService = aiInvestigationService;
    }

    @EventListener
    public void onIncidentCreated(IncidentCreatedEvent event) {
        log.info("Received IncidentCreatedEvent for incident [{}], service [{}]", event.incidentId(), event.serviceName());
        try {
            IncidentContextBundle bundle = correlationService.buildContextBundle(event.incidentId());
            incidentContextCacheService.cacheContextBundle(bundle);
            log.info("Successfully correlated and cached context bundle in Redis for incident [{}]", event.incidentId());

            // Autonomous investigation trigger
            aiInvestigationService.investigateIncident(event.incidentId());
            log.info("Completed AI investigation for incident [{}]", event.incidentId());
        } catch (Exception ex) {
            log.error("Failed to process incident investigation for incident [{}]: {}", event.incidentId(), ex.getMessage(), ex);
        }
    }
}
