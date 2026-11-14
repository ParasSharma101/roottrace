package com.roottrace.workspace.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertDetailDto(
    UUID id,
    String source,
    String alertType,
    String serviceName,
    String rawPayload,
    Instant triggeredAt
) {}
