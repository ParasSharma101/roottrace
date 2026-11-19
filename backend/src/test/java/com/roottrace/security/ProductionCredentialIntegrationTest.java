package com.roottrace.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "roottrace.security.break-glass.enabled=true")
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionCredentialIntegrationTest extends WorkspaceSessionTestSupport {

    @Autowired private MockMvc mockMvc;

    @Test
    void emergencyHeaderCannotAccessProtectedApi() throws Exception {
        mockMvc.perform(get("/api/v1/incidents")
                        .header(BreakGlassAuthenticationFilter.BREAK_GLASS_HEADER, "roottrace_emergency_breakglass_token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emergencyHeaderCannotUpgradeViewerPermissions() throws Exception {
        Fixture fixture = fixture("ROLE_VIEWER");
        String token = tokens.generateToken(fixture.user().getId(), fixture.workspace().getId());
        mockMvc.perform(post("/api/v1/incidents/00000000-0000-0000-0000-000000000001/notes")
                        .header("Authorization", "Bearer " + token)
                        .header(BreakGlassAuthenticationFilter.BREAK_GLASS_HEADER, "roottrace_emergency_breakglass_token")
                        .contentType("application/json").content("{\"note\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ingestionCredentialCannotReadWorkspaceDetails() throws Exception {
        mockMvc.perform(get("/api/v1/workspace")
                        .header(WebhookApiKeyFilter.API_KEY_HEADER, "test-only-ingestion-key-at-least-32-bytes-long"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oldDevelopmentSigningSecretCannotMintAcceptedTokens() throws Exception {
        MockEnvironment development = new MockEnvironment();
        development.setActiveProfiles("dev");
        JwtTokenProvider oldTokens = new JwtTokenProvider(new SecuritySettings(development));
        Fixture fixture = fixture("ROLE_VIEWER");
        String forgedToken = oldTokens.generateToken(fixture.user().getId(), fixture.workspace().getId());
        mockMvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());
        String validToken = tokens.generateToken(fixture.user().getId(), fixture.workspace().getId());
        mockMvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
    }
}
