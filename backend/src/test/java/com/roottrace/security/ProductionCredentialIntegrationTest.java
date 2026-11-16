package com.roottrace.security;

import com.roottrace.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "roottrace.security.break-glass.enabled=true")
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionCredentialIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokens;

    @Test
    void emergencyHeaderCannotAccessProtectedApi() throws Exception {
        mockMvc.perform(get("/api/v1/incidents")
                        .header(BreakGlassAuthenticationFilter.BREAK_GLASS_HEADER, "roottrace_emergency_breakglass_token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emergencyHeaderCannotUpgradeViewerPermissions() throws Exception {
        String token = tokens.generateToken("viewer@example.com", "Viewer", List.of("ROLE_VIEWER"));
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
        String forgedToken = oldTokens.generateToken("attacker@example.com", "Attacker", List.of("ROLE_ADMIN"));
        mockMvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());
        String validToken = tokens.generateToken("reader@example.com", "Reader", List.of("ROLE_VIEWER"));
        mockMvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
    }
}
