package com.justjava.humanresource.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mobile/auth")
@RequiredArgsConstructor
public class MobileTokenController {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final ObjectMapper objectMapper;

    @Value("${keycloak.base-url}")
    private String keycloakBaseUrl;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Value("${spring.security.oauth2.client.registration.keycloak-mobile.client_id}")
    private String mobileClientId;

    @Value("${spring.security.oauth2.client.registration.keycloak-mobile.client_secret}")
    private String mobileClientSecret;

    @GetMapping("/tokens")
    public TokenResponse exportCurrentTokens(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("No authenticated session found.");
        }

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                "keycloak-mobile",
                authentication.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("No mobile OAuth2 client tokens available.");
        }

        TokenResponse response = new TokenResponse();
        response.setAccessToken(client.getAccessToken().getTokenValue());
        response.setRefreshToken(client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null);
        response.setTokenType("Bearer");
        response.setExpiresIn(client.getAccessToken().getExpiresAt() != null
                ? Math.max(0, client.getAccessToken().getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond())
                : null);
        return response;
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new IllegalArgumentException("refreshToken is required");
        }

        String tokenEndpoint = keycloakBaseUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", mobileClientId);
        form.add("client_secret", mobileClientSecret);
        form.add("refresh_token", request.getRefreshToken());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = new RestTemplate().postForEntity(
                tokenEndpoint,
                new HttpEntity<>(form, headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Failed to refresh token.");
        }

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            TokenResponse tokenResponse = new TokenResponse();
            tokenResponse.setAccessToken(json.path("access_token").asText(null));
            tokenResponse.setRefreshToken(json.path("refresh_token").asText(null));
            tokenResponse.setTokenType(json.path("token_type").asText("Bearer"));
            tokenResponse.setExpiresIn(json.has("expires_in") ? json.path("expires_in").asLong() : null);
            return tokenResponse;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse token response", e);
        }
    }

    @PostMapping("/session/login")
    public SessionLoginResponse loginSessionWithAccessToken(
            @RequestBody SessionLoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        if (request.getAccessToken() == null || request.getAccessToken().isBlank()) {
            throw new IllegalArgumentException("accessToken is required");
        }

        String userInfoEndpoint = keycloakBaseUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(request.getAccessToken());
        ResponseEntity<String> response = new RestTemplate().exchange(
                userInfoEndpoint,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Unable to validate access token.");
        }

        try {
            JsonNode userInfo = objectMapper.readTree(response.getBody());
            Map<String, Object> claims = objectMapper.convertValue(userInfo, Map.class);
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

            JsonNode groups = userInfo.path("groups");
            if (groups.isArray()) {
                Iterator<JsonNode> iterator = groups.elements();
                while (iterator.hasNext()) {
                    authorities.add(new SimpleGrantedAuthority(iterator.next().asText()));
                }
            }

            OidcIdToken oidcIdToken = new OidcIdToken(
                    "mobile-session-token",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    claims
            );
            DefaultOidcUser oidcUser = new DefaultOidcUser(authorities, oidcIdToken, "preferred_username");

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    oidcUser,
                    null,
                    oidcUser.getAuthorities()
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession session = httpServletRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            SessionLoginResponse loginResponse = new SessionLoginResponse();
            loginResponse.setAuthenticated(true);
            loginResponse.setRedirectUrl("/mobile/employee/dashboard");
            return loginResponse;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to establish server session from access token.", e);
        }
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    @Data
    public static class SessionLoginRequest {
        private String accessToken;
    }

    @Data
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long expiresIn;
    }

    @Data
    public static class SessionLoginResponse {
        private boolean authenticated;
        private String redirectUrl;
    }
}

