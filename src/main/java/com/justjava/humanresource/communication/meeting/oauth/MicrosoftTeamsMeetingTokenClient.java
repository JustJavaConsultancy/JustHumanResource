package com.justjava.humanresource.communication.meeting.oauth;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class MicrosoftTeamsMeetingTokenClient extends AbstractMeetingTokenClient implements MeetingTokenClient {

    private final MeetingIntegrationProperties properties;
    private final RestClient restClient;

    public MicrosoftTeamsMeetingTokenClient(MeetingIntegrationProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public MeetingProvider provider() {
        return MeetingProvider.MICROSOFT_TEAMS;
    }

    @Override
    public boolean isConfigured() {
        MeetingIntegrationProperties.Microsoft microsoft = properties.getProviders().getMicrosoft();
        return microsoft.isEnabled()
                && hasText(microsoft.getTenantId())
                && hasText(microsoft.getClientId())
                && hasText(microsoft.getClientSecret())
                && hasText(microsoft.getDefaultOrganizerUserId());
    }

    @Override
    public MeetingAccessToken fetchToken() {
        if (!isConfigured()) {
            throw new MeetingCredentialException("Microsoft Teams meeting integration is not fully configured");
        }
        MeetingIntegrationProperties.Microsoft microsoft = properties.getProviders().getMicrosoft();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", microsoft.getClientId());
        form.add("client_secret", microsoft.getClientSecret());
        form.add("scope", "https://graph.microsoft.com/.default");
        form.add("grant_type", "client_credentials");
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token", microsoft.getTenantId())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return tokenFrom(response == null ? Map.of() : response);
        } catch (RestClientException ex) {
            throw new MeetingCredentialException("Could not obtain Microsoft Teams access token", ex);
        }
    }
}
