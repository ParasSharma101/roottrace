package com.roottrace.security;

import com.roottrace.domain.model.WorkspaceMember;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceAuthenticationIntegrationTest extends WorkspaceSessionTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwords;
    @Autowired private SecuritySettings settings;

    @Test
    void passwordLoginRequiresExplicitWorkspaceSelection() throws Exception {
        Fixture fixture = fixture("ROLE_ADMIN");
        fixture.user().setPasswordHash(passwords.encode("test-password"));
        users.saveAndFlush(fixture.user());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + fixture.user().getEmail() + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workspaceId").isEmpty())
                .andExpect(jsonPath("$.roles").isEmpty()).andExpect(jsonPath("$.apiKey").isEmpty());
        String account = tokens.generateToken(fixture.user().getId(), null);
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + account))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/workspaces").header("Authorization", "Bearer " + account))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].workspaceId").value(fixture.workspace().getId().toString()));
    }

    @Test
    void switchingUsesRoleAndDetailsOfSelectedWorkspace() throws Exception {
        Fixture admin = fixture("ROLE_ADMIN");
        Fixture other = fixture("ROLE_ADMIN");
        memberships.saveAndFlush(new WorkspaceMember(other.workspace(), admin.user(), "ROLE_VIEWER"));
        String account = tokens.generateToken(admin.user().getId(), null);
        String body = mvc.perform(post("/api/v1/auth/select-workspace").header("Authorization", "Bearer " + account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceId\":\"" + other.workspace().getId() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_VIEWER"))
                .andExpect(jsonPath("$.apiKey").isEmpty()).andReturn().getResponse().getContentAsString();
        String selected = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("token").asText();
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + selected))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(other.workspace().getId().toString()))
                .andExpect(jsonPath("$.apiKey").isEmpty());
        mvc.perform(post("/api/v1/workspace/invites").header("Authorization", "Bearer " + selected)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\",\"role\":\"ROLE_SRE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonMemberAndUnknownWorkspacesReturnSameNotFound() throws Exception {
        Fixture user = fixture("ROLE_ADMIN");
        Fixture stranger = fixture("ROLE_ADMIN");
        String token = tokens.generateToken(user.user().getId(), null);
        for (UUID id : List.of(stranger.workspace().getId(), UUID.randomUUID(), AuthDataSeeder.DEFAULT_WORKSPACE_ID)) {
            mvc.perform(post("/api/v1/auth/select-workspace").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"workspaceId\":\"" + id + "\"}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/auth/workspaces").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void roleChangesApplyWithoutIssuingAnotherToken() throws Exception {
        Fixture fixture = fixture("ROLE_ADMIN");
        String token = tokens.generateToken(fixture.user().getId(), fixture.workspace().getId());
        fixture.member().setRole("ROLE_VIEWER");
        memberships.saveAndFlush(fixture.member());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_VIEWER"))
                .andExpect(jsonPath("$.apiKey").isEmpty());
        mvc.perform(post("/api/v1/workspace/invites").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\",\"role\":\"ROLE_SRE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void removedMembershipInvalidatesSelectedWorkspaceToken() throws Exception {
        Fixture fixture = fixture("ROLE_ADMIN");
        String token = tokens.generateToken(fixture.user().getId(), fixture.workspace().getId());
        memberships.delete(fixture.member());
        memberships.flush();
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        String account = tokens.generateToken(fixture.user().getId(), null);
        mvc.perform(get("/api/v1/auth/workspaces").header("Authorization", "Bearer " + account))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deletedUserAndPreviousTokenFormatAreRejected() throws Exception {
        Fixture fixture = fixture("ROLE_ADMIN");
        String legacy = Jwts.builder().subject(fixture.user().getEmail()).claim("roles", List.of("ROLE_ADMIN"))
                .signWith(Keys.hmacShaKeyFor(settings.getJwtSecret().getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + legacy))
                .andExpect(status().isUnauthorized());
        String token = tokens.generateToken(UUID.randomUUID(), null);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
