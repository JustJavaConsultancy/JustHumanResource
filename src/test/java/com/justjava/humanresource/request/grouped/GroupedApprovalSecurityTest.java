package com.justjava.humanresource.request.grouped;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Map;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GroupedApprovalSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @Configuration @EnableWebSecurity @EnableWebMvc @Import(GroupedApprovalSecurity.class)
    static class WebConfiguration {
        @Bean ProbeController probeController() { return new ProbeController(); }
    }
    @RestController
    static class ProbeController {
        @GetMapping("/api/requests/grouped-approvals/csrf") Map<String,String> token(CsrfToken token) { return Map.of("token",token.getToken()); }
        @PostMapping("/api/requests/grouped-approvals/decisions") void decide() {}
        @PostMapping("/api/grouped-approval-paths") void configure() {}
    }
    @BeforeEach void start() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(WebConfiguration.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    @Test void anonymousCannotReadGroupedEndpoints() throws Exception {
        mvc.perform(get("/api/requests/grouped-approvals/csrf")).andExpect(status().isUnauthorized());
    }
    @Test void authenticatedMutationRequiresCsrf() throws Exception {
        mvc.perform(post("/api/requests/grouped-approvals/decisions").with(user("approver"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/grouped-approval-paths").with(user("admin"))).andExpect(status().isForbidden());
    }
    @Test void authenticatedTokenCanSubmitAndGetMaterializesCookie() throws Exception {
        // Materialize the real repository cookie before Spring Security's csrf() test helper installs its mock repository.
        mvc.perform(get("/api/requests/grouped-approvals/csrf").with(user("approver")))
                .andExpect(status().isOk()).andExpect(cookie().exists("XSRF-TOKEN"));
        mvc.perform(post("/api/requests/grouped-approvals/decisions").with(user("approver")).with(csrf().asHeader())).andExpect(status().isOk());
    }
}
