package com.roottrace.correlation.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeploymentSummary(
        UUID deploymentId,
        String serviceName,
        Instant deploymentTime,
        String versionTag,
        String commitHash,
        String environment,
        List<PRSummary> pullRequests
) {}
