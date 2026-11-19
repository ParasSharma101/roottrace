package com.roottrace.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="management.endpoints.web.base-path=/manage")
@AutoConfigureMockMvc
class ManagementBasePathIntegrationTest extends WorkspaceSessionTestSupport {
    @Autowired private MockMvc mvc;
    @Test
    void changingManagementPathCannotExposeGlobalDiagnosticsToWorkspaceUsers() throws Exception {
        Fixture admin=fixture("ROLE_ADMIN");
        String token="Bearer "+tokens.generateToken(admin.user().getId(),admin.workspace().getId());
        for(String path:new String[]{"/manage","/manage/info","/manage/metrics","/manage/health"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization",token)).andExpect(status().isForbidden());
        }
    }
}
