package com.roottrace.correlation.listener;

import com.roottrace.correlation.model.IncidentContextBundle;
import com.roottrace.correlation.service.CorrelationService;
import com.roottrace.correlation.service.IncidentContextCacheService;
import com.roottrace.domain.event.IncidentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class IncidentEventListener {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventListener.class);

    private final CorrelationService correlationService;
    private final IncidentContextCacheService incidentContextCacheService;

    public IncidentEventListener(CorrelationService correlationService,
                                 IncidentContextCacheService incidentContextCacheService) {
        this.correlationService = correlationService;
        this.incidentContextCacheService = incidentContextCacheService;
    }

    @EventListener
    public void onIncidentCreated(IncidentCreatedEvent event) {
        log.info("Received IncidentCreatedEvent for incident [{}], service [{}]", event.incidentId(), event.serviceName());
        try {
            IncidentContextBundle bundle = correlationService.buildContextBundle(event.incidentId());
            incidentContextCacheService.cacheContextBundle(bundle);
            log.info("Successfully correlated and cached context bundle in Redis for incident [{}]", event.incidentId());
        } catch (Exception ex) {
            log.error("Failed to build/cache context bundle for incident [{}]: {}", event.incidentId(), ex.getMessage(), ex);
        }
    }
}
