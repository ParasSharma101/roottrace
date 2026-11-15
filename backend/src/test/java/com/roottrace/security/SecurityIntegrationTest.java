package com.roottrace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.model.Hypothesis;
import com.roottrace.domain.model.HypothesisStatus;
import com.roottrace.domain.model.Incident;
import com.roottrace.domain.model.IncidentNote;
import com.roottrace.domain.model.IncidentStatus;
import com.roottrace.domain.model.SeverityLevel;
import com.roottrace.domain.repository.HypothesisRepository;
import com.roottrace.domain.repository.IncidentNoteRepository;
import com.roottrace.domain.repository.IncidentRepository;
import com.roottrace.security.dto.DemoLoginRequest;
import com.roottrace.security.dto.LoginRequest;
import com.roottrace.workspace.dto.VerifyHypothesisRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private HypothesisRepository hypothesisRepository;
    @Autowired private IncidentNoteRepository incidentNoteRepository;

    @Test
    @DisplayName("GET /api/v1/incidents - should reject unauthenticated request with 401")
    void testUnauthenticatedIncidentsAccess() throws Exception {
        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/demo-login - should mint valid JWT for SRE, VIEWER, and ADMIN")
    void testDemoLogin() throws Exception {
        DemoLoginRequest req = new DemoLoginRequest("SRE");

        mockMvc.perform(post("/api/v1/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("alice.sre@roottrace.io"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_SRE"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - should authenticate and return JWT token")
    void testStandardLogin() throws Exception {
        LoginRequest req = new LoginRequest("sre.lead@company.com", "any-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("sre.lead@company.com"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me - should return authenticated profile from JWT")
    void testGetMe() throws Exception {
        String token = jwtTokenProvider.generateToken("alice@roottrace.io", "Alice", List.of("ROLE_SRE"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@roottrace.io"))
                .andExpect(jsonPath("$.name").value("alice"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_SRE"))
                .andExpect(jsonPath("$.breakGlass").value(false));
    }

    @Test
    @DisplayName("RBAC - ROLE_VIEWER can read incidents but gets 403 Forbidden on verify hypothesis")
    void testViewerRolePermissions() throws Exception {
        String viewerToken = jwtTokenProvider.generateToken("viewer@roottrace.io", "Viewer", List.of("ROLE_VIEWER"));

        Incident inc = incidentRepository.save(new Incident("Test Incident", SeverityLevel.SEV2, IncidentStatus.INVESTIGATING));
        Hypothesis hyp = hypothesisRepository.save(new Hypothesis(inc, "Hypothesis 1", BigDecimal.valueOf(0.8), HypothesisStatus.PENDING, "[]"));

        // GET allowed for VIEWER
        mockMvc.perform(get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        // POST verify hypothesis rejected for VIEWER with 403 Forbidden
        mockMvc.perform(post("/api/v1/incidents/{id}/hypotheses/{hypId}/verify", inc.getId(), hyp.getId())
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyHypothesisRequest("viewer@roottrace.io"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - ROLE_SRE can verify hypothesis and stamps authenticated user identity into audit note")
    void testSreRoleVerifyHypothesis() throws Exception {
        String sreToken = jwtTokenProvider.generateToken("alice.sre@roottrace.io", "Alice", List.of("ROLE_SRE"));

        Incident inc = incidentRepository.save(new Incident("Test Incident SRE", SeverityLevel.SEV1, IncidentStatus.INVESTIGATING));
        Hypothesis hyp = hypothesisRepository.save(new Hypothesis(inc, "Database pool exhaustion", BigDecimal.valueOf(0.95), HypothesisStatus.PENDING, "[]"));

        mockMvc.perform(post("/api/v1/incidents/{id}/hypotheses/{hypId}/verify", inc.getId(), hyp.getId())
                        .header("Authorization", "Bearer " + sreToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        // Verify that audit note automatically stamped the authenticated JWT email
        List<IncidentNote> notes = incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(inc.getId());
        assertThat(notes).isNotEmpty();
        assertThat(notes.getFirst().getAuthor()).isEqualTo("alice.sre@roottrace.io");
    }

    @Test
    @DisplayName("Emergency Break-Glass - should authenticate with Break-Glass header and allow admin operations")
    void testEmergencyBreakGlassAccess() throws Exception {
        Incident inc = incidentRepository.save(new Incident("Outage Incident", SeverityLevel.SEV1, IncidentStatus.INVESTIGATING));
        Hypothesis hyp = hypothesisRepository.save(new Hypothesis(inc, "Core router failure", BigDecimal.valueOf(0.99), HypothesisStatus.PENDING, "[]"));

        mockMvc.perform(post("/api/v1/incidents/{id}/hypotheses/{hypId}/verify", inc.getId(), hyp.getId())
                        .header(BreakGlassAuthenticationFilter.BREAK_GLASS_HEADER, "roottrace_emergency_breakglass_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        List<IncidentNote> notes = incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(inc.getId());
        assertThat(notes).isNotEmpty();
        assertThat(notes.getFirst().getAuthor()).isEqualTo("emergency-breakglass@roottrace.internal");
    }
}
