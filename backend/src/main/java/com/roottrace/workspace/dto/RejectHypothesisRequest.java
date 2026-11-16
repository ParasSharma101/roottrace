package com.roottrace.workspace.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectHypothesisRequest(
    String author,
    String reason
) {}
