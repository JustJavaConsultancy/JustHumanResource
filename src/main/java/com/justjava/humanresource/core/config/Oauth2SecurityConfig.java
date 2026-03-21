package com.justjava.humanresource.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Slf4j
@Configuration
public class Oauth2SecurityConfig {

    @Bean
    protected SecurityFilterChain configure(HttpSecurity http, HandlerMappingIntrospector introspector,ClientRegistrationRepository repo) throws Exception {
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
                        .successHandler(authenticationSuccessHandler())
                        )
                .authorizeHttpRequests(
                        authorize -> {
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

    private AuthenticationSuccessHandler authenticationSuccessHandler(){
        return  (request, response, authentication) -> {
            if (request.getRequestURI().contains("keycloak-mobile")){
                response.sendRedirect("/mobile/employee/dashboard");
            }else if(request.getRequestURI().contains("keycloak-web")){
                response.sendRedirect("/employee/dashboard");
            }else {
                response.sendRedirect("/");
            }
        };
    }
}