package com.roottrace.workspace.dto;

import com.roottrace.domain.model.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateIncidentStatusRequest(
    @NotNull(message = "status is required")
    IncidentStatus status
) {}
