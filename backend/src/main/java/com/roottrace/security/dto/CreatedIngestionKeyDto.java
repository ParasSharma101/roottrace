package com.roottrace.security.dto;

public record CreatedIngestionKeyDto(IngestionKeyDto key, String secret) {
    @Override
    public String toString() { return "CreatedIngestionKeyDto[key=" + key.id() + ", secret=REDACTED]"; }
}
