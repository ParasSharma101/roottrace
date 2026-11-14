package com.roottrace.domain.event;

import java.time.Instant;
import java.util.UUID;

public record IncidentCreatedEvent(
        UUID incidentId,
        String serviceName,
        Instant triggeredAt
) {}
