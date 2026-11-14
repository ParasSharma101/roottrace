package com.roottrace.workspace.dto;

import java.time.Instant;
import java.util.Map;

public record TimelineEventDto(
    String id,
    Instant timestamp,
    TimelineEventType eventType,
    String title,
    String description,
    Map<String, Object> metadata,
    String severity
) {}
