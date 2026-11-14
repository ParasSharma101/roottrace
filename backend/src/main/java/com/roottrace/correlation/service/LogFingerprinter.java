package com.roottrace.correlation.service;

import com.roottrace.correlation.model.CompressedLogSignature;
import com.roottrace.domain.model.LogEvent;
import com.roottrace.domain.model.LogLevel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class LogFingerprinter {

    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern IP_PATTERN = Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?\\b");
    private static final Pattern NUM_PATTERN = Pattern.compile("\\b\\d+\\b");

    public List<CompressedLogSignature> compress(List<LogEvent> rawLogs) {
        if (rawLogs == null || rawLogs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, SignatureAccumulator> map = new LinkedHashMap<>();

        for (LogEvent log : rawLogs) {
            String signature = extractSignature(log);
            String groupKey = log.getLogLevel().name() + "::" + signature;

            map.compute(groupKey, (key, existing) -> {
                if (existing == null) {
                    return new SignatureAccumulator(
                            signature,
                            log.getLogLevel(),
                            log.getTimestamp(),
                            log.getMessage(),
                            log.getStackTrace(),
                            log.getId()
                    );
                } else {
                    existing.recordOccurrence(log.getTimestamp(), log.getMessage(), log.getStackTrace(), log.getId());
                    return existing;
                }
            });
        }

        List<CompressedLogSignature> result = new ArrayList<>();
        for (SignatureAccumulator acc : map.values()) {
            result.add(acc.toCompressedSignature());
        }

        // Sort by frequency descending (most frequent error spikes on top)
        result.sort((a, b) -> Long.compare(b.count(), a.count()));
        return result;
    }

    private String extractSignature(LogEvent log) {
        if (log.getStackTrace() != null && !log.getStackTrace().isBlank()) {
            // First line of stack trace usually contains exception class and description
            String[] lines = log.getStackTrace().split("\n|\r\n");
            if (lines.length > 0 && !lines[0].isBlank()) {
                return normalizeText(lines[0].trim());
            }
        }
        return normalizeText(log.getMessage());
    }

    private String normalizeText(String text) {
        if (text == null) return "Unknown";
        String normalized = UUID_PATTERN.matcher(text).replaceAll("<UUID>");
        normalized = IP_PATTERN.matcher(normalized).replaceAll("<IP>");
        normalized = NUM_PATTERN.matcher(normalized).replaceAll("<NUM>");
        return normalized.trim();
    }

    private static class SignatureAccumulator {
        private final String signature;
        private final LogLevel logLevel;
        private long count;
        private Instant firstSeen;
        private Instant lastSeen;
        private String sampleMessage;
        private String sampleStackTrace;
        private UUID sampleLogId;

        public SignatureAccumulator(String signature, LogLevel logLevel, Instant timestamp,
                                    String message, String stackTrace, UUID logId) {
            this.signature = signature;
            this.logLevel = logLevel;
            this.count = 1;
            this.firstSeen = timestamp;
            this.lastSeen = timestamp;
            this.sampleMessage = message;
            this.sampleStackTrace = stackTrace;
            this.sampleLogId = logId;
        }

        public void recordOccurrence(Instant timestamp, String message, String stackTrace, UUID logId) {
            this.count++;
            if (timestamp.isBefore(this.firstSeen)) {
                this.firstSeen = timestamp;
            }
            if (timestamp.isAfter(this.lastSeen)) {
                this.lastSeen = timestamp;
                this.sampleMessage = message;
                this.sampleStackTrace = stackTrace;
                this.sampleLogId = logId;
            }
        }

        public CompressedLogSignature toCompressedSignature() {
            return new CompressedLogSignature(
                    signature,
                    logLevel,
                    count,
                    firstSeen,
                    lastSeen,
                    sampleMessage,
                    sampleStackTrace,
                    sampleLogId
            );
        }
    }
}
