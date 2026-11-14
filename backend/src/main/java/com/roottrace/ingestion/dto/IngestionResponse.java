package com.roottrace.ingestion.dto;

import java.time.Instant;
import java.util.UUID;

public record IngestionResponse(
    String status,
    UUID trackingId,
    int count,
    Instant receivedAt
) {
    public static IngestionResponse accepted(UUID trackingId, int count) {
        return new IngestionResponse("ACCEPTED", trackingId, count, Instant.now());
    }

    public static IngestionResponse accepted(UUID trackingId) {
        return accepted(trackingId, 1);
    }
}
