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
public class ZoomMeetingProviderClient implements MeetingProviderClient {

    private final MeetingIntegrationProperties properties;
    private final MeetingTokenService tokenService;
    private final RestClient restClient = RestClient.builder().build();

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
    public CreatedMeeting createMeeting(CreateMeetingRequest request) {
        if (!isConfigured()) {
            throw new MeetingProviderException("Zoom meeting provider is not configured");
        }
        MeetingIntegrationProperties.Zoom zoom = properties.getProviders().getZoom();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topic", request.subject());
        body.put("type", request.instantMeeting() ? 1 : 2);
        body.put("agenda", request.agenda());
        if (!request.instantMeeting()) {
            body.put("start_time", request.startTime().toOffsetDateTime().toString());
            body.put("duration", request.durationMinutes());
        }
        body.put("settings", Map.of(
                "waiting_room", true,
                "join_before_host", false,
                "mute_upon_entry", true
        ));

        try {
            Map<String, Object> response = restClient.post()
                    .uri(zoom.getApiBaseUrl() + "/users/{userId}/meetings", zoom.getDefaultHostUserId())
                    .header("Authorization", tokenService.accessToken(provider()).authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return new CreatedMeeting(
                    stringValue(response, "id"),
                    stringValue(response, "join_url"),
                    stringValue(response, "start_url"),
                    "Zoom meeting created"
            );
        } catch (RestClientException ex) {
            throw new MeetingProviderException("Zoom meeting creation failed", ex);
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
