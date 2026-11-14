package com.roottrace.ai.dto;

import java.util.List;

public record AiInvestigationResult(
        String summary,
        List<String> suspiciousChanges,
        List<AiHypothesisItem> hypotheses
) {}
