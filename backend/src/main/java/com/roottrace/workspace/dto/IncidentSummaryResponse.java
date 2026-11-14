package com.roottrace.workspace.dto;

import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.model.SeverityLevel;
import java.time.Instant;
import java.util.UUID;

public record IncidentSummaryResponse(
    UUID id,
    String title,
    IncidentStatus status,
    SeverityLevel severityLevel,
    String serviceName,
    Instant startTime,
    Instant resolutionTime,
    int alertCount,
    int hypothesisCount
) {}
