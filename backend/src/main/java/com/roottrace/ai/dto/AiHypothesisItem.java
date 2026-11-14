package com.roottrace.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiHypothesisItem(
        String description,
        BigDecimal confidenceScore,
        List<AiEvidenceCitation> evidenceCitations
) {}
