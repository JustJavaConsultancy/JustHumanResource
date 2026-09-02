package com.justjava.humanresource.communication.meeting.provider;

import com.google.apps.meet.v2.Space;
import com.google.apps.meet.v2.SpacesServiceClient;
import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleMeetMeetingProviderClient implements MeetingProviderClient {

    private final MeetingIntegrationProperties properties;
    private final GoogleMeetClientFactory googleMeetClientFactory;

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
        try (SpacesServiceClient client = googleMeetClientFactory.create(google)) {
            Space response = client.createSpace(Space.newBuilder().build());
            return new CreatedMeeting(
                    response.getName(),
                    response.getMeetingUri(),
                    null,
                    "Google Meet space created"
            );
        } catch (RuntimeException ex) {
            throw new MeetingProviderException("Google Meet creation failed", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
