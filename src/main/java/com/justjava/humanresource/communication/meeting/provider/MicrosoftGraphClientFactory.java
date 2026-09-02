package com.justjava.humanresource.communication.meeting.provider;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.stereotype.Component;

@Component
public class MicrosoftGraphClientFactory {

    private static final String GRAPH_DEFAULT_SCOPE = "https://graph.microsoft.com/.default";

    public GraphServiceClient create(MeetingIntegrationProperties.Microsoft microsoft) {
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(microsoft.getTenantId())
                .clientId(microsoft.getClientId())
                .clientSecret(microsoft.getClientSecret())
                .build();
        return new GraphServiceClient(credential, GRAPH_DEFAULT_SCOPE);
    }
}
