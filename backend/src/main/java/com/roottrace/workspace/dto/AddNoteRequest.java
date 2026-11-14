package com.roottrace.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record AddNoteRequest(
    @NotBlank(message = "author is required")
    String author,
    @NotBlank(message = "note content is required")
    String note,
    UUID hypothesisId
) {}
