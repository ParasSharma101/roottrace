package com.roottrace.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialFilterTest {

    private static final String INGESTION = "test-only-ingestion-key-of-at-least-32-bytes";

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void matchingEmergencyTokenCannotAuthenticateInProduction() throws Exception {
        MockEnvironment environment = environment();
        environment.setActiveProfiles("dev", "prod");
        environment.setProperty("roottrace.security.break-glass.enabled", "true");
        environment.setProperty("roottrace.security.break-glass.token", "emergency-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspace");
        request.addHeader(BreakGlassAuthenticationFilter.BREAK_GLASS_HEADER, "emergency-token");
        new BreakGlassAuthenticationFilter(new SecuritySettings(environment))
                .doFilter(request, new MockHttpServletResponse(), (req, res) ->
                        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    @Test
    void ingestionCredentialCannotAuthenticateAsAnApplicationUser() throws Exception {
        MockHttpServletRequest request = request("/api/v1/workspace", INGESTION);
        new WebhookApiKeyFilter(new SecuritySettings(environment()), keys())
                .doFilter(request, new MockHttpServletResponse(), (req, res) ->
                        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    @Test
    void validIngestionCredentialWorksOnWebhooksWithContextPath() throws Exception {
        MockHttpServletRequest request = request("/roottrace/api/v1/webhooks/logs", INGESTION);
        request.setContextPath("/roottrace");
        new WebhookApiKeyFilter(new SecuritySettings(environment()), keys())
                .doFilter(request, new MockHttpServletResponse(), (req, res) ->
                        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                                .isEqualTo("ingestion-service"));
    }

    @Test
    void incorrectIngestionCredentialDoesNotAuthenticate() throws Exception {
        new WebhookApiKeyFilter(new SecuritySettings(environment()), keys())
                .doFilter(request("/api/v1/webhooks/logs", "wrong-key"), new MockHttpServletResponse(),
                        (req, res) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    private com.roottrace.security.service.IngestionKeyService keys() {
        var keys = org.mockito.Mockito.mock(com.roottrace.security.service.IngestionKeyService.class);
        org.mockito.Mockito.when(keys.resolveWorkspace(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.Optional.empty());
        return keys;
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("roottrace.security.jwt.secret", "test-only-signing-key-of-at-least-32-bytes")
                .withProperty("roottrace.security.ingestion.api-key", INGESTION);
    }

    private MockHttpServletRequest request(String path, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(WebhookApiKeyFilter.API_KEY_HEADER, key);
        return request;
    }
}
