package com.roottrace.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceSetupIntegrationTest extends WorkspaceSessionTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Test
    void emptyWorkspaceDoesNotInheritAnotherTenantsConnectionEvidence() throws Exception {
        var owner=fixture("ROLE_VIEWER"); var other=fixture("ROLE_ADMIN");
        jdbc.update("insert into ingestion_submissions(id,workspace_id,request_kind,payload_hash,item_count) values (?,?,'logs',?,1)",UUID.randomUUID(),other.workspace().getId(),"a".repeat(64));
        jdbc.update("insert into ingestion_receipts(workspace_id,event_id,event_type,payload_hash) values (?,?,'LOG',?)",other.workspace().getId(),UUID.randomUUID(),"b".repeat(64));
        mvc.perform(get("/api/v1/workspace/setup-status").header("Authorization","Bearer "+tokens.generateToken(owner.user().getId(),owner.workspace().getId())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.activeKeyCount").value(0)).andExpect(jsonPath("$.acceptedRequestCount").value(0))
                .andExpect(jsonPath("$.processedEventCount").value(0)).andExpect(jsonPath("$.lastProcessedAt").isEmpty());
    }
    @Test
    void acceptedRequestAndConsumerProcessingAreReportedSeparatelyWithoutCredentials() throws Exception {
        var owner=fixture("ROLE_VIEWER"); UUID ws=owner.workspace().getId();
        jdbc.update("insert into workspace_ingestion_keys(id,workspace_id,name,key_hash,key_prefix) values (?,?,'test',?,'rt_ing_fixture')",UUID.randomUUID(),ws,"a".repeat(64));
        jdbc.update("insert into workspace_ingestion_keys(id,workspace_id,name,key_hash,key_prefix,revoked_at) values (?,?,'revoked',?,'rt_ing_retired',now())",UUID.randomUUID(),ws,"c".repeat(64));
        jdbc.update("insert into ingestion_submissions(id,workspace_id,request_kind,payload_hash,item_count) values (?,?,'logs',?,1)",UUID.randomUUID(),ws,"b".repeat(64));
        String token="Bearer "+tokens.generateToken(owner.user().getId(),ws);
        mvc.perform(get("/api/v1/workspace/setup-status").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeKeyCount").value(1)).andExpect(jsonPath("$.acceptedRequestCount").value(1))
                .andExpect(jsonPath("$.processedEventCount").value(0)).andExpect(jsonPath("$.lastRequestAt").isNotEmpty())
                .andExpect(jsonPath("$.keyHash").doesNotExist()).andExpect(jsonPath("$.keyPrefix").doesNotExist());
        jdbc.update("insert into ingestion_receipts(workspace_id,event_id,event_type,payload_hash) values (?,?,'LOG',?)",ws,UUID.randomUUID(),"d".repeat(64));
        mvc.perform(get("/api/v1/workspace/setup-status").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.processedEventCount").value(1)).andExpect(jsonPath("$.lastProcessedAt").isNotEmpty());
    }
    @Test
    void accountOnlyAndRemovedWorkspaceSessionsCannotReadSetupEvidence() throws Exception {
        var owner=fixture("ROLE_ADMIN");
        mvc.perform(get("/api/v1/workspace/setup-status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/workspace/setup-status").header("Authorization","Bearer "+tokens.generateToken(owner.user().getId(),null))).andExpect(status().isForbidden());
        String selected="Bearer "+tokens.generateToken(owner.user().getId(),owner.workspace().getId());
        memberships.delete(owner.member()); memberships.flush();
        mvc.perform(get("/api/v1/workspace/setup-status").header("Authorization",selected)).andExpect(status().isUnauthorized());
    }
}
