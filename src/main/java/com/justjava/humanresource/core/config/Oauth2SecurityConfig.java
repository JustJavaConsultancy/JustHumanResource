package com.justjava.humanresource.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class Oauth2SecurityConfig {
    private static final String HR_REGISTRATION_ID = "keycloak";
    private static final String MOBILE_REGISTRATION_ID = "keycloak-mobile";
    private static final String MOBILE_WEB_REGISTRATION_ID = "keycloak-web";

    private static final Set<String> HR_ALLOWED_GROUPS = Set.of(
            "employees",
            "financialofficers",
            "finance",
            "admin",
            "humanresource",
            "jobhr",
            "restrictedhr"
    );

    private static final Set<String> MOBILE_ALLOWED_GROUPS = Set.of(
            "employees",
            "assetmanager",
            "auditor",
            "departmenthead"
    );

    @Bean
    protected SecurityFilterChain configure(HttpSecurity http, HandlerMappingIntrospector introspector,ClientRegistrationRepository repo) throws Exception {
        log.debug("Configuring security");

        http.securityMatcher("/**")
                .anonymous(Customizer.withDefaults())
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer
                                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                )
                .csrf(CsrfConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .authorizationEndpoint(Customizer.withDefaults())
                        .tokenEndpoint(Customizer.withDefaults())
                        .userInfoEndpoint(Customizer.withDefaults())
                        .successHandler(authenticationSuccessHandler(repo))
                )
                .authorizeHttpRequests(
                        authorize -> {
                            authorize.requestMatchers(new AntPathRequestMatcher("/login")).permitAll();
                            authorize.requestMatchers(new AntPathRequestMatcher("/careers/**")).permitAll();
                            authorize.requestMatchers(new AntPathRequestMatcher("/mobile/biometric/bootstrap")).permitAll();
                            authorize.requestMatchers(new AntPathRequestMatcher("/mobile/auth/refresh")).permitAll();
                            authorize.requestMatchers(new AntPathRequestMatcher("/mobile/auth/session/login")).permitAll();
                            authorize.requestMatchers(new AntPathRequestMatcher("/api/**")).permitAll();
                            authorize.anyRequest().authenticated();
                        }
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(repo))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutUrl("/users/logout")
                );
        return http.build();
    }



    private LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository repository) {
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(repository);
        logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
        return logoutSuccessHandler;
    }

    private LogoutSuccessHandler unauthorizedGroupLogoutSuccessHandler(ClientRegistrationRepository repository) {
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(repository);
        logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
        return logoutSuccessHandler;
    }

    private AuthenticationSuccessHandler authenticationSuccessHandler(ClientRegistrationRepository repository){
        return  (request, response, authentication) -> {
            String registrationId = resolveRegistrationId(request, authentication);
            if (!hasAllowedRealmGroup(registrationId, authentication)) {
                log.warn("Rejecting OAuth login for registration '{}' because user has no allowed realm group", registrationId);
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                SecurityContextHolder.clearContext();
                unauthorizedGroupLogoutSuccessHandler(repository).onLogoutSuccess(request, response, authentication);
                return;
            }

            if (MOBILE_REGISTRATION_ID.equals(registrationId)){
                response.sendRedirect("/mobile/employee/dashboard");
            }else if(MOBILE_WEB_REGISTRATION_ID.equals(registrationId)){
                response.sendRedirect("/employee/dashboard");
            }else {
                response.sendRedirect("/");
            }
        };
    }

    private String resolveRegistrationId(jakarta.servlet.http.HttpServletRequest request, Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            return oauthToken.getAuthorizedClientRegistrationId();
        }

        String requestUri = request.getRequestURI();
        if (requestUri.contains(MOBILE_REGISTRATION_ID)) {
            return MOBILE_REGISTRATION_ID;
        }
        if (requestUri.contains(MOBILE_WEB_REGISTRATION_ID)) {
            return MOBILE_WEB_REGISTRATION_ID;
        }
        return HR_REGISTRATION_ID;
    }

    private boolean hasAllowedRealmGroup(String registrationId, Authentication authentication) {
        Set<String> allowedGroups = allowedGroupsForRegistration(registrationId);
        Set<String> userGroups = normalizedGroups(authentication);
        return userGroups.stream().anyMatch(allowedGroups::contains);
    }

    private Set<String> allowedGroupsForRegistration(String registrationId) {
        if (MOBILE_REGISTRATION_ID.equals(registrationId) || MOBILE_WEB_REGISTRATION_ID.equals(registrationId)) {
            return MOBILE_ALLOWED_GROUPS;
        }
        return HR_ALLOWED_GROUPS;
    }

    private Set<String> normalizedGroups(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof DefaultOidcUser oidcUser)) {
            return Set.of();
        }

        Object groupsClaim = oidcUser.getClaims().get("groups");
        if (!(groupsClaim instanceof Collection<?> groups)) {
            return Set.of();
        }

        return groups.stream()
                .map(String::valueOf)
                .map(this::normalizeGroup)
                .filter(group -> !group.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalizeGroup(String group) {
        String normalizedGroup = group.trim();
        if (normalizedGroup.startsWith("/")) {
            normalizedGroup = normalizedGroup.substring(1);
        }
        return normalizedGroup.toLowerCase(Locale.ROOT);
    }
}
