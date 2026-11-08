package com.justjava.humanresource.integration.inbound;

import com.justjava.humanresource.core.config.IntegrationSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



/**
 * STAGE 1 tests for the JustHR integration security chain (D3).
 * Starts ONLY the controller and the integration security config (see TestApp below),
 * so none of the rest of JustHR (interceptors, other config) is loaded.
 * No Keycloak needed: the decoder is replaced by a fake.
 */
@WebMvcTest
@Import({IntegrationSecurityConfig.class, IntegrationWhoAmIController.class})
@TestPropertySource(properties = {
        "integration.security.issuer-uri=https://test-issuer.invalid/realms/humanResources",
        "integration.security.audience=humanresource-api"
})
class IntegrationSecurityTest {

    /** Stops the test from scanning (and loading) the whole application. */
    @Configuration
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    // Replaces the real decoder so no network call is made.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void noToken_isRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/integrations/whoami"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidToken_isRejectedWith401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get("/api/integrations/whoami")
                        .header("Authorization", "Bearer this-is-not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudience_isRejectedWith401() throws Exception {
        when(jwtDecoder.decode("wrong-audience")).thenThrow(new BadJwtException("wrong audience"));

        mockMvc.perform(get("/api/integrations/whoami")
                        .header("Authorization", "Bearer wrong-audience"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongClient_isForbiddenOnEveryIntegrationRoute() throws Exception {
        mockMvc.perform(get("/api/integrations/whoami")
                        .with(jwt().jwt(j -> j.claim("azp", "other-client"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/integrations/payroll-journals/PAY-2026-10-57")
                        .with(jwt().jwt(j -> j
                                .claim("azp", "other-client")
                                .claim("scope", "payroll-journal:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingReadScope_isForbidden() throws Exception {
        mockMvc.perform(get("/api/integrations/payroll-journals/PAY-2026-10-57")
                        .with(jwt().jwt(j -> j.claim("azp", "jjeip-humanresource"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingStatusWriteScope_isForbidden() throws Exception {
        mockMvc.perform(post("/api/integrations/payroll-journals/PAY-2026-10-57/status")
                        .contentType(APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().jwt(j -> j.claim("azp", "jjeip-humanresource"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void validToken_returnsClientIdAndScopes() throws Exception {
        mockMvc.perform(get("/api/integrations/whoami")
                        .with(jwt().jwt(j -> j
                                .claim("azp", "jjeip-humanresource")
                                .claim("scope", "payroll-status:write payroll-journal:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("jjeip-humanresource"))
                .andExpect(jsonPath("$.scopes[0]").value("payroll-status:write"));
    }

    @Test
    void correctTokenPassesJournalReadAndStatusWriteAuthorization() throws Exception {
        mockMvc.perform(get("/api/integrations/payroll-journals/PAY-2026-10-57")
                        .with(jwt().jwt(j -> j
                                .claim("azp", "jjeip-humanresource")
                                .claim("scope", "payroll-journal:read"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/integrations/payroll-journals/PAY-2026-10-57/status")
                        .contentType(APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().jwt(j -> j
                                .claim("azp", "jjeip-humanresource")
                                .claim("scope", "payroll-status:write"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void loggedInBrowserSession_cannotCallIntegrationPaths() throws Exception {
        // Simulates a user who is logged in through the browser: the login is stored in the HTTP session.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "ui-user", null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        mockMvc.perform(get("/api/integrations/whoami").session(session))
                .andExpect(status().isUnauthorized());
    }
}