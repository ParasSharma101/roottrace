package com.roottrace.ai.dto;

import java.util.UUID;

public record AiEvidenceCitation(
        String type,
        UUID refId,
        String snippet
) {}
