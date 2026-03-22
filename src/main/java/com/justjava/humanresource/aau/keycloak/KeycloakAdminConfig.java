package com.justjava.humanresource.aau.keycloak;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.keycloak.OAuth2Constants;
import org.springframework.context.annotation.Primary;

@Configuration
public class KeycloakAdminConfig {

    @Value("${keycloak.base-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.base-realm}")
    private String baseRealm;
    @Value("${keycloak.admin-client-id}")
    private String clientId;

    @Value("${keycloak.client-id}")
    private String baseClientId;

    @Value("${keycloak.admin-client-secret}")
    private String clientSecret;

    @Value("${keycloak.client-secret}")
    private String baseClientSecret;
    @Bean
    @Qualifier("adminKeycloak")
    @Primary
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder
                .builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
    }
    @Bean(value = "baseKeycloak")
    @Qualifier("baseKeycloak")
    public Keycloak keycloakBaseClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(baseRealm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(baseClientId)
                .clientSecret(baseClientSecret)
                .build();
    }
}