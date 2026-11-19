package com.roottrace.security;

import com.roottrace.support.IntegrationTestSupport;
import com.roottrace.workspace.controller.SimulationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
abstract class DemoAccessDisabledTestSupport extends IntegrationTestSupport {

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mockMvc;

    @Test
    void demoControllersAndAccountSeederAreNotRegistered() {
        assertThat(context.getBeansOfType(DemoAuthController.class)).isEmpty();
        assertThat(context.getBeansOfType(SimulationController.class)).isEmpty();
        assertThat(context.getBeansOfType(AuthDataSeeder.class)).isEmpty();
    }

    @Test
    void anonymousRequestsCannotUseDemoRoutes() throws Exception {
        assertDemoRoutesAbsent();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotUseDisabledDemoRoutes() throws Exception {
        assertDemoRoutesAbsent();
    }

    private void assertDemoRoutesAbsent() throws Exception {
        for (String role : new String[]{"SRE", "VIEWER", "ADMIN"}) {
            mockMvc.perform(post("/api/v1/auth/demo-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"" + role + "\"}"))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(post("/api/v1/simulation/scenario-a")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/simulation/scenario-b")).andExpect(status().isNotFound());
    }
}
