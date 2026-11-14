package com.roottrace.correlation.model;

import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.model.SeverityLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentContextBundle(
        UUID incidentId,
        String incidentTitle,
        SeverityLevel severityLevel,
        IncidentStatus status,
        String serviceName,
        Instant incidentStartTime,
        Instant windowStart,
        Instant windowEnd,
        List<AlertSummary> alerts,
        List<DeploymentSummary> deployments,
        List<CompressedLogSignature> compressedLogs,
        long totalRawLogsCount,
        Instant generatedAt
) {}
