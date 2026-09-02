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
public class GoogleMeetMeetingTokenClient extends AbstractMeetingTokenClient implements MeetingTokenClient {

    private final MeetingIntegrationProperties properties;
    private final RestClient restClient;

    public GoogleMeetMeetingTokenClient(MeetingIntegrationProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public MeetingProvider provider() {
        return MeetingProvider.GOOGLE_MEET;
    }

    @Override
    public boolean isConfigured() {
        MeetingIntegrationProperties.Google google = properties.getProviders().getGoogle();
        return google.isEnabled()
                && hasText(google.getClientId())
                && hasText(google.getClientSecret())
                && hasText(google.getRefreshToken());
    }

    @Override
    public MeetingAccessToken fetchToken() {
        if (!isConfigured()) {
            throw new MeetingCredentialException("Google Meet integration is not fully configured");
        }
        MeetingIntegrationProperties.Google google = properties.getProviders().getGoogle();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", google.getClientId());
        form.add("client_secret", google.getClientSecret());
        form.add("refresh_token", google.getRefreshToken());
        form.add("grant_type", "refresh_token");
        try {
            Map<String, Object> response = restClient.post()
                    .uri(google.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return tokenFrom(response == null ? Map.of() : response);
        } catch (RestClientException ex) {
            throw new MeetingCredentialException("Could not obtain Google Meet access token", ex);
        }
    }
}
