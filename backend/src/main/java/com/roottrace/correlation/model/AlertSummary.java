package com.roottrace.correlation.model;

import java.time.Instant;
import java.util.UUID;

public record AlertSummary(
        UUID alertId,
        String source,
        String alertType,
        String serviceName,
        Instant triggeredAt,
        String rawPayload
) {}
