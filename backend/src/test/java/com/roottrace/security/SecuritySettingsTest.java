package com.roottrace.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecuritySettingsTest {

    private static final String JWT = "test-only-signing-secret-of-at-least-32-bytes";
    private static final String INGESTION = "test-only-ingestion-key-of-at-least-32-bytes";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SecuritySettings.class);

    @Test
    void defaultConfigurationFailsWithoutSecrets() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "Invalid security configuration: roottrace.security.jwt.secret must be configured without surrounding whitespace");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"roottrace.security.jwt.secret", "roottrace.security.ingestion.api-key"})
    void missingRequiredCredentialFails(String property) {
        validRunner().withPropertyValues(property + "=").run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "short-secret", " secret-with-surrounding-whitespace-at-least-32-bytes ",
            "roottrace-super-secret-jwt-signing-key-minimum-256-bits-for-hs256-security!",
            "roottrace_emergency_breakglass_token", "roottrace_dev_secret_key"
    })
    void invalidCredentialsFailWithoutDisclosingValues(String secret) {
        for (String property : new String[]{"roottrace.security.jwt.secret", "roottrace.security.ingestion.api-key"}) {
            validRunner().withInitializer(context -> context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("credential-under-test", Map.of(property, secret))))
                    .run(context -> {
                assertThat(context).hasFailed();
                Throwable cause = context.getStartupFailure();
                while (cause.getCause() != null) cause = cause.getCause();
                assertThat(cause.getMessage()).contains(property);
                if (!secret.isEmpty()) assertThat(cause.getMessage()).doesNotContain(secret);
            });
        }
    }

    @Test
    void credentialsMustBeSeparate() {
        validRunner().withPropertyValues("roottrace.security.ingestion.api-key=" + JWT)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void nonPositiveTokenLifetimeFails() {
        validRunner().withPropertyValues("roottrace.security.jwt.expiration-ms=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void developmentCanUseLocalDefaultsButBreakGlassIsOffByDefault() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SecuritySettings.class).isBreakGlassEnabled()).isFalse();
        });
    }

    @Test
    void explicitDevelopmentOptInAllowsBreakGlass() {
        runner.withPropertyValues("spring.profiles.active=dev", "roottrace.security.break-glass.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SecuritySettings.class).isBreakGlassEnabled()).isTrue();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev,prod", "staging"})
    void breakGlassCannotBeEnabledOutsideDevelopment(String profiles) {
        validRunner().withPropertyValues("spring.profiles.active=" + profiles,
                        "roottrace.security.break-glass.enabled=true",
                        "roottrace.security.break-glass.token=matching-but-disabled-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SecuritySettings.class).isBreakGlassEnabled()).isFalse();
                    assertThat(context.getBean(SecuritySettings.class).getBreakGlassToken()).isEmpty();
                });
    }

    @Test
    void combinedDevAndProdCannotUseDevelopmentDefaults() {
        runner.withPropertyValues("spring.profiles.active=dev,prod")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner validRunner() {
        return runner.withPropertyValues("roottrace.security.jwt.secret=" + JWT,
                "roottrace.security.ingestion.api-key=" + INGESTION);
    }
}
