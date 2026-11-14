package com.roottrace.workspace.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectHypothesisRequest(
    @NotBlank(message = "author is required")
    String author,
    String reason
) {}
