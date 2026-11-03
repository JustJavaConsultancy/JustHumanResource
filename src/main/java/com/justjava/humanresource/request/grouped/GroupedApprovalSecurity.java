package com.justjava.humanresource.request.grouped;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/** New routes opt into session authentication and CSRF without changing legacy API clients. */
@Configuration
public class GroupedApprovalSecurity {
    @Bean @Order(1)
    SecurityFilterChain groupedApprovalSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/grouped-approval-paths/**", "/api/requests/grouped-approvals/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, error) -> response.sendError(401)));
        return http.build();
    }
}
