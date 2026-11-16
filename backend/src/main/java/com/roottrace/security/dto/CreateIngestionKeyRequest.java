package com.roottrace.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIngestionKeyRequest(@NotBlank @Size(max = 128) String name) {}
