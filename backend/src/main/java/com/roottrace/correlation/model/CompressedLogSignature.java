package com.roottrace.correlation.model;

import com.roottrace.domain.model.LogLevel;
import java.time.Instant;
import java.util.UUID;

public record CompressedLogSignature(
        String signature,
        LogLevel logLevel,
        long count,
        Instant firstSeen,
        Instant lastSeen,
        String sampleMessage,
        String sampleStackTrace,
        UUID sampleLogId
) {}
