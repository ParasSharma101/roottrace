package com.roottrace.workspace.dto;

import java.time.Instant;
import java.util.UUID;

public record PostmortemResponse(
    UUID incidentId,
    String title,
    String markdownContent,
    Instant generatedAt
) {}
