package com.justjava.humanresource.core.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;



/**
 * STAGE 1 (D3): bearer-token security for service-to-service calls.
 * Applies ONLY to /api/integrations/**. All other paths stay with Oauth2SecurityConfig.
 * Uses the main HR realm issuer only (never the mobile realm).
 */
@Slf4j
@Configuration
public class IntegrationSecurityConfig {

    private static final String INTEGRATION_PATH = "/api/integrations/**";

    // Defaults to the main HR realm issuer already configured for login. No secrets here.
    @Value("${integration.security.issuer-uri:${spring.security.oauth2.client.provider.keycloak.issuer-uri}}")
    private String issuerUri;

    @Value("${integration.security.audience:humanresource-api}")
    private String requiredAudience;

    @Value("${integration.payroll-accounting.allowed-client-id:${integration.security.allowed-client-id:jjeip-humanresource}}")
    private String allowedClientId;

    @Bean
    @Order(2)
    public SecurityFilterChain integrationSecurityFilterChain(HttpSecurity http,
                                                              JwtDecoder integrationJwtDecoder) throws Exception {
        http.securityMatcher(INTEGRATION_PATH)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(CsrfConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/integrations/payroll-journals/**")
                        .access((authentication, context) ->
                                new AuthorizationDecision(hasAllowedClient(authentication.get())
                                        && hasScope(authentication.get(), "payroll-journal:read")))
                        .requestMatchers(HttpMethod.POST, "/api/integrations/payroll-journals/*/status")
                        .access((authentication, context) ->
                                new AuthorizationDecision(hasAllowedClient(authentication.get())
                                        && hasScope(authentication.get(), "payroll-status:write")))
                        .anyRequest()
                        .access((authentication, context) ->
                                new AuthorizationDecision(hasAllowedClient(authentication.get()))))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(integrationJwtDecoder)));
        return http.build();
    }

    private boolean hasAllowedClient(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwtAuthentication
                && allowedClientId.equals(jwtAuthentication.getToken().getClaimAsString("azp"));
    }

    private boolean hasScope(Authentication authentication, String requiredScope) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ("SCOPE_" + requiredScope).equals(authority.getAuthority()));
    }

    @Bean
    public JwtDecoder integrationJwtDecoder() {
        return new LazyJwtDecoder();
    }

    /**
     * Contacts the issuer only when the first token arrives, so the application can start
     * even if Keycloak is briefly unreachable. If the issuer cannot be reached, the token is
     * rejected (never accepted) and the next request tries again.
     */
    private final class LazyJwtDecoder implements JwtDecoder {
        private volatile JwtDecoder delegate;

        @Override
        public Jwt decode(String token) throws JwtException {
            JwtDecoder current = delegate;
            if (current == null) {
                synchronized (this) {
                    current = delegate;
                    if (current == null) {
                        try {
                            current = buildDecoder();
                        } catch (RuntimeException e) {
                            log.warn("Integration issuer configuration could not be loaded; token rejected");
                            throw new JwtException("Unable to load issuer configuration", e);
                        }
                        delegate = current;
                    }
                }
            }
            return current.decode(token);
        }
    }

    private JwtDecoder buildDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(tokenValidator(issuerUri, requiredAudience));
        return decoder;
    }

    /** Issuer + expiry + required audience. Package-private and static so tests can call it directly. */
    static OAuth2TokenValidator<Jwt> tokenValidator(String issuerUri, String requiredAudience) {
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri); // issuer + expiry
        OAuth2TokenValidator<Jwt> withAudience = jwt -> {
            List<String> audience = jwt.getAudience();
            if (audience != null && audience.contains(requiredAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required audience is missing", null));
        };
        return new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);
    }
}