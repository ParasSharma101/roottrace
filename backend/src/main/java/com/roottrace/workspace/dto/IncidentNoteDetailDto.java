package com.roottrace.workspace.dto;

import java.time.Instant;
import java.util.UUID;

public record IncidentNoteDetailDto(
    UUID id,
    UUID incidentId,
    UUID hypothesisId,
    String author,
    String note,
    Instant createdAt
) {}
