package com.roottrace.infrastructure.github;

import java.time.Instant;
import java.util.List;

public record GitHubCommitPRMetadata(
        String commitHash,
        int prNumber,
        String title,
        String author,
        String diffSummary,
        List<String> filesChanged,
        Instant mergedAt
) {}
