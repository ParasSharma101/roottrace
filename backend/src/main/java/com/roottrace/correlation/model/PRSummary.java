package com.roottrace.correlation.model;

import java.time.Instant;
import java.util.UUID;

public record PRSummary(
        UUID prId,
        int prNumber,
        String title,
        String author,
        String diffSummary,
        String filesChanged,
        Instant mergedAt
) {}
