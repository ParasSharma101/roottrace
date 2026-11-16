package com.roottrace.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record AlertWebhookRequest(
    @NotBlank(message = "serviceName must not be blank")
    @jakarta.validation.constraints.Size(max = 128)
    String serviceName,

    @NotBlank(message = "alertType must not be blank")
    @jakarta.validation.constraints.Size(max = 128)
    String alertType,

    String severity,

    @NotNull(message = "triggeredAt must not be null (ISO-8601 UTC required, e.g. 2026-11-14T08:00:00Z)")
    Instant triggeredAt,

    Map<String, Object> details
) {}
