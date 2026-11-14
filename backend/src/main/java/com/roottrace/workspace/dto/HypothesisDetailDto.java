package com.roottrace.workspace.dto;

import com.roottrace.domain.model.HypothesisStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HypothesisDetailDto(
    UUID id,
    UUID incidentId,
    String description,
    BigDecimal confidenceScore,
    HypothesisStatus status,
    String evidenceCitations,
    Instant createdAt,
    Instant updatedAt
) {}
