package com.roottrace.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Validates credentials without including their values in errors or diagnostics. */
@Component
public class SecuritySettings {

    private static final String DEV_JWT =
            "roottrace-super-secret-jwt-signing-key-minimum-256-bits-for-hs256-security!";
    private static final String DEV_INGESTION = "roottrace_dev_secret_key";
    private static final String DEV_BREAK_GLASS = "roottrace_emergency_breakglass_token";
    private static final Set<String> KNOWN_DEFAULTS = Set.of(
            DEV_JWT, DEV_INGESTION, DEV_BREAK_GLASS,
            "roottrace_dev_password", "roottrace_secret_change_me",
            "your_jwt_secret_here", "your_ingestion_key_here", "change-me");

    private final String jwtSecret;
    private final long jwtExpirationMs;
    private final String ingestionKey;
    private final boolean breakGlassEnabled;
    private final String breakGlassToken;

    public SecuritySettings(Environment environment) {
        boolean development = environment.acceptsProfiles(Profiles.of("dev & !prod"));
        jwtSecret = resolve(environment, "roottrace.security.jwt.secret", development, DEV_JWT);
        ingestionKey = resolve(environment, "roottrace.security.ingestion.api-key", development, DEV_INGESTION);
        validateSecret("roottrace.security.jwt.secret", jwtSecret, development);
        validateSecret("roottrace.security.ingestion.api-key", ingestionKey, development);
        if (jwtSecret.equals(ingestionKey)) {
            throw invalid("roottrace.security.ingestion.api-key", "must differ from the JWT signing secret");
        }
        jwtExpirationMs = environment.getProperty("roottrace.security.jwt.expiration-ms", Long.class, 86400000L);
        if (jwtExpirationMs <= 0) {
            throw invalid("roottrace.security.jwt.expiration-ms", "must be positive");
        }
        // An enable flag or token can never activate this bypass outside local development.
        breakGlassEnabled = development && environment.getProperty(
                "roottrace.security.break-glass.enabled", Boolean.class, false);
        breakGlassToken = breakGlassEnabled
                ? resolve(environment, "roottrace.security.break-glass.token", true, DEV_BREAK_GLASS)
                : "";
        if (breakGlassEnabled) {
            validateSecret("roottrace.security.break-glass.token", breakGlassToken, true);
        }
        if (breakGlassEnabled && (breakGlassToken.equals(jwtSecret) || breakGlassToken.equals(ingestionKey))) {
            throw invalid("roottrace.security.break-glass.token", "must use a separate credential");
        }
    }

    private static String resolve(Environment environment, String property, boolean development, String fallback) {
        String configured = environment.getProperty(property, "");
        return development && configured.isEmpty() ? fallback : configured;
    }

    private static void validateSecret(String property, String value, boolean development) {
        if (value.isBlank() || !value.equals(value.strip())) {
            throw invalid(property, "must be configured without surrounding whitespace");
        }
        if (!development && (KNOWN_DEFAULTS.contains(value) || value.getBytes(StandardCharsets.UTF_8).length < 32)) {
            throw invalid(property, "must be a non-default secret of at least 32 UTF-8 bytes");
        }
        if (property.equals("roottrace.security.jwt.secret") && value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw invalid(property, "must contain at least 32 UTF-8 bytes");
        }
    }

    private static IllegalStateException invalid(String property, String requirement) {
        return new IllegalStateException("Invalid security configuration: " + property + " " + requirement);
    }

    public String getJwtSecret() { return jwtSecret; }
    public long getJwtExpirationMs() { return jwtExpirationMs; }
    public String getIngestionKey() { return ingestionKey; }
    public boolean isBreakGlassEnabled() { return breakGlassEnabled; }
    public String getBreakGlassToken() { return breakGlassToken; }
}
