package com.justjava.humanresource.communication.meeting.provider;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import com.justjava.humanresource.communication.meeting.oauth.MeetingTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleMeetMeetingProviderClient implements MeetingProviderClient {

    private final MeetingIntegrationProperties properties;
    private final MeetingTokenService tokenService;
    private final RestClient restClient = RestClient.builder().build();

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
    public CreatedMeeting createMeeting(CreateMeetingRequest request) {
        if (!isConfigured()) {
            throw new MeetingProviderException("Google Meet provider is not configured");
        }
        MeetingIntegrationProperties.Google google = properties.getProviders().getGoogle();
        try {
            Map<String, Object> response = restClient.post()
                    .uri(google.getMeetBaseUrl() + "/spaces")
                    .header("Authorization", tokenService.accessToken(provider()).authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(Map.class);
            return new CreatedMeeting(
                    stringValue(response, "name"),
                    stringValue(response, "meetingUri"),
                    null,
                    "Google Meet space created"
            );
        } catch (RestClientException ex) {
            throw new MeetingProviderException("Google Meet creation failed", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String stringValue(Map<String, Object> response, String key) {
        if (response == null || response.get(key) == null) {
            return null;
        }
        return String.valueOf(response.get(key));
    }
}
