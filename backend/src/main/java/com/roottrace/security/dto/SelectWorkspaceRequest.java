package com.roottrace.security.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SelectWorkspaceRequest(@NotNull UUID workspaceId) {}
