package com.roottrace.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class DefaultDemoDisabledIntegrationTest extends DemoAccessDisabledTestSupport {

    @Autowired private MockMvc mockMvc;

    @Test
    @Transactional
    void normalSignupAndPasswordLoginRemainAvailable() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"test-password-123\","
                                + "\"fullName\":\"Test Owner\",\"workspaceName\":\"Test Workspace\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
