package com.justjava.humanresource.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** STAGE 1 (D4 hardening): Flowable's own REST paths must be refused for everyone. */
@WebMvcTest
@Import(FlowableRestBlockConfig.class)
class FlowableRestBlockConfigTest {

    /** Stops the test from scanning (and loading) the whole application. */
    @Configuration
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymous_isForbidden() throws Exception {
        mockMvc.perform(get("/process-api/runtime/tasks")).andExpect(status().isForbidden());
    }

    @Test
    void loggedInUser_isForbidden() throws Exception {
        mockMvc.perform(get("/process-api/runtime/tasks").with(user("someone").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void completingATask_isForbidden() throws Exception {
        mockMvc.perform(put("/process-api/runtime/tasks/abc")
                        .with(user("someone").roles("USER"))
                        .contentType("application/json")
                        .content("{\"action\":\"complete\"}"))
                .andExpect(status().isForbidden());
    }
}