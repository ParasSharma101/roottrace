package com.roottrace.security.dto;

import java.time.Instant;
import java.util.UUID;

public record IngestionKeyDto(UUID id, String name, String prefix, Instant createdAt, Instant revokedAt) {}
