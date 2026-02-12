package com.justjava.humanresource.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Slf4j
@Configuration
public class Oauth2SecurityConfig {

    @Bean
    protected SecurityFilterChain configure(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        log.debug("Configuring security");

        http.securityMatcher("/**")
                .anonymous(AnonymousConfigurer::disable)
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer
                                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                )
                .csrf(CsrfConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
//                        .loginPage("/login") // custom login page
                        .authorizationEndpoint(Customizer.withDefaults())
                        .tokenEndpoint(Customizer.withDefaults())
                        .userInfoEndpoint(Customizer.withDefaults())
//                        .successHandler(authenticationSuccessHandler())
                        )
                .authorizeHttpRequests(
                        authorize -> {
                           authorize.requestMatchers(new AntPathRequestMatcher("/api/**")).permitAll();
                            authorize.anyRequest().authenticated();
                        }
                )
                .logout(logout -> logout
                        .invalidateHttpSession(false)
                        .logoutUrl("/users/logout"));
        return http.build();
    }
}
