package com.justjava.humanresource.communication.meeting.provider;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import com.justjava.humanresource.communication.meeting.oauth.MeetingTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MicrosoftTeamsMeetingProviderClient implements MeetingProviderClient {

    private final MeetingIntegrationProperties properties;
    private final MeetingTokenService tokenService;
    private final RestClient restClient = RestClient.builder().build();

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
    public CreatedMeeting createMeeting(CreateMeetingRequest request) {
        if (!isConfigured()) {
            throw new MeetingProviderException("Microsoft Teams meeting provider is not configured");
        }
        MeetingIntegrationProperties.Microsoft microsoft = properties.getProviders().getMicrosoft();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", request.subject());
        body.put("startDateTime", request.startTime().toOffsetDateTime().toString());
        body.put("endDateTime", request.endTime().toOffsetDateTime().toString());

        try {
            Map<String, Object> response = restClient.post()
                    .uri(microsoft.getGraphBaseUrl() + "/users/{userId}/onlineMeetings", microsoft.getDefaultOrganizerUserId())
                    .header("Authorization", tokenService.accessToken(provider()).authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return new CreatedMeeting(
                    stringValue(response, "id"),
                    stringValue(response, "joinWebUrl"),
                    null,
                    "Microsoft Teams meeting created"
            );
        } catch (RestClientException ex) {
            throw new MeetingProviderException("Microsoft Teams meeting creation failed", ex);
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
