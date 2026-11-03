package com.justjava.humanresource.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;


@Configuration
public class FlowableRestBlockConfig {

    @Bean
    @Order(3) // after GroupedApprovalSecurity (1) and IntegrationSecurityConfig (2); before the catch-all login chain
    SecurityFilterChain flowableRestBlockFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/process-api/**", "/dmn-api/**", "/form-api/**",
                        "/cmmn-api/**", "/content-api/**")
                .authorizeHttpRequests(a -> a.anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)));
        return http.build();
    }
}