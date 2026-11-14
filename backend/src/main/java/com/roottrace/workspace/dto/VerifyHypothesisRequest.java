package com.roottrace.workspace.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyHypothesisRequest(
    @NotBlank(message = "author is required")
    String author
) {}
