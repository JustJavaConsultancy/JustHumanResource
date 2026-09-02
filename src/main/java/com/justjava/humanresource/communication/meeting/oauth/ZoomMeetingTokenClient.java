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
public class ZoomMeetingTokenClient extends AbstractMeetingTokenClient implements MeetingTokenClient {

    private final MeetingIntegrationProperties properties;
    private final RestClient restClient;

    public ZoomMeetingTokenClient(MeetingIntegrationProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public MeetingProvider provider() {
        return MeetingProvider.ZOOM;
    }

    @Override
    public boolean isConfigured() {
        MeetingIntegrationProperties.Zoom zoom = properties.getProviders().getZoom();
        return zoom.isEnabled()
                && hasText(zoom.getAccountId())
                && hasText(zoom.getClientId())
                && hasText(zoom.getClientSecret());
    }

    @Override
    public MeetingAccessToken fetchToken() {
        if (!isConfigured()) {
            throw new MeetingCredentialException("Zoom meeting integration is not fully configured");
        }
        MeetingIntegrationProperties.Zoom zoom = properties.getProviders().getZoom();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "account_credentials");
        form.add("account_id", zoom.getAccountId());
        try {
            Map<String, Object> response = restClient.post()
                    .uri(zoom.getTokenUrl())
                    .headers(headers -> headers.setBasicAuth(zoom.getClientId(), zoom.getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return tokenFrom(response == null ? Map.of() : response);
        } catch (RestClientException ex) {
            throw new MeetingCredentialException("Could not obtain Zoom access token", ex);
        }
    }
}
