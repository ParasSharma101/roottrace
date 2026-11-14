package com.roottrace.workspace.dto;

import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.model.SeverityLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentDetailResponse(
    UUID id,
    String title,
    String summary,
    IncidentStatus status,
    SeverityLevel severityLevel,
    String serviceName,
    Instant startTime,
    Instant resolutionTime,
    Instant createdAt,
    Instant updatedAt,
    List<AlertDetailDto> alerts,
    List<HypothesisDetailDto> hypotheses,
    List<IncidentNoteDetailDto> notes
) {}
