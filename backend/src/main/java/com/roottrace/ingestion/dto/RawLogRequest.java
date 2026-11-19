package com.roottrace.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record RawLogRequest(
    @NotBlank(message = "serviceName must not be blank")
    @jakarta.validation.constraints.Size(max = 128)
    String serviceName,

    @NotNull(message = "timestamp must not be null (ISO-8601 UTC required, e.g. 2026-11-14T08:00:00Z)")
    Instant timestamp,

    @NotBlank(message = "logLevel must not be blank")
    String logLevel,

    @NotBlank(message = "message must not be blank")
    String message,

    String stackTrace,

    Map<String, Object> metadata
) {}
