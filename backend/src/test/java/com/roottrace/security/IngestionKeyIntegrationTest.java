package com.roottrace.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.domain.repository.WorkspaceIngestionKeyRepository;
import com.roottrace.security.service.IngestionKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionKeyIntegrationTest extends WorkspaceSessionTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private WorkspaceIngestionKeyRepository keys;
    @Autowired private IngestionKeyService resolver;

    @Test
    void multipleKeysAreIndependentHashedAndSecretIsReturnedOnlyOnCreation() throws Exception {
        Fixture owner = fixture("ROLE_ADMIN");
        JsonNode first = create(owner, "Monitoring");
        JsonNode second = create(owner, "CI integration");
        String raw = first.get("secret").asText();
        UUID id = UUID.fromString(first.get("key").get("id").asText());
        assertThat(raw).startsWith("rt_ing_").hasSize(50);
        assertThat(second.get("secret").asText()).isNotEqualTo(raw);
        assertThat(keys.findById(id).orElseThrow().getKeyHash()).hasSize(64).isNotEqualTo(raw);
        assertThat(resolver.resolveWorkspace(raw)).contains(owner.workspace().getId());
        String list = mvc.perform(get(path()).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(raw, second.get("secret").asText(), "keyHash", "secret");
        mvc.perform(get(path() + "/" + id).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.keyHash").doesNotExist());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.apiKey").isEmpty());
        mvc.perform(get("/api/v1/workspace").header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.apiKey").isEmpty());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete(path() + "/" + id).header("Authorization", token(owner)))
                    .andExpect(status().isNoContent());
        }
        assertThat(resolver.resolveWorkspace(raw)).isEmpty();
        assertThat(resolver.resolveWorkspace(second.get("secret").asText())).contains(owner.workspace().getId());
        mvc.perform(get(path() + "/" + id).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revokedAt").isNotEmpty());
    }

    @Test
    void keyManagementCannotCrossWorkspaceOrRoleBoundaries() throws Exception {
        Fixture owner = fixture("ROLE_ADMIN");
        UUID id = UUID.fromString(create(owner, "Owner key").get("key").get("id").asText());
        Fixture stranger = fixture("ROLE_ADMIN");
        for (UUID requested : new UUID[]{id, UUID.randomUUID()}) {
            mvc.perform(delete(path() + "/" + requested).header("Authorization", token(stranger)))
                    .andExpect(status().isNotFound());
            mvc.perform(get(path() + "/" + requested).header("Authorization", token(stranger)))
                    .andExpect(status().isNotFound());
        }
        for (String role : new String[]{"ROLE_VIEWER", "ROLE_SRE"}) {
            Fixture limited = fixture(role);
            mvc.perform(get(path()).header("Authorization", token(limited))).andExpect(status().isForbidden());
            mvc.perform(post(path()).header("Authorization", token(limited)).contentType("application/json")
                            .content("{\"name\":\"test\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(path() + "/" + id).header("Authorization", token(limited)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidNamesAndUnknownCredentialsAreRejected() throws Exception {
        Fixture owner = fixture("ROLE_ADMIN");
        for (String name : new String[]{"", "   ", "x".repeat(129)}) {
            mvc.perform(post(path()).header("Authorization", token(owner)).contentType("application/json")
                            .content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(resolver.resolveWorkspace(null)).isEmpty();
        assertThat(resolver.resolveWorkspace("wrong-key")).isEmpty();
        assertThat(resolver.resolveWorkspace("x".repeat(257))).isEmpty();
    }

    @Test
    void legacyWorkspaceCannotIssueIntegrationCredentialsEvenWithMembership() {
        Fixture owner = fixture("ROLE_ADMIN");
        var legacy = workspaces.findById(AuthDataSeeder.DEFAULT_WORKSPACE_ID).orElseThrow();
        memberships.saveAndFlush(new com.roottrace.domain.model.WorkspaceMember(legacy, owner.user(), "ROLE_ADMIN"));
        var identity = new WorkspaceIdentity(owner.user().getId(), owner.user().getEmail(), "Test", legacy.getId(), "ROLE_ADMIN");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.create(identity, "Not allowed"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private JsonNode create(Fixture owner, String name) throws Exception {
        return mapper.readTree(mvc.perform(post(path()).header("Authorization", token(owner)).contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
    }

    private String token(Fixture owner) {
        return "Bearer " + tokens.generateToken(owner.user().getId(), owner.workspace().getId());
    }

    private String path() { return "/api/v1/workspace/ingestion-keys"; }
}
