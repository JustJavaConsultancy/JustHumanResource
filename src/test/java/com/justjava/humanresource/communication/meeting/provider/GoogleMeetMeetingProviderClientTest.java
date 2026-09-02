package com.justjava.humanresource.communication.meeting.provider;

import com.google.apps.meet.v2.Space;
import com.google.apps.meet.v2.SpacesServiceClient;
import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleMeetMeetingProviderClientTest {

    private final GoogleMeetClientFactory googleMeetClientFactory = mock(GoogleMeetClientFactory.class);
    private final MeetingIntegrationProperties properties = new MeetingIntegrationProperties();
    private final GoogleMeetMeetingProviderClient client =
            new GoogleMeetMeetingProviderClient(properties, googleMeetClientFactory);

    @Test
    void reportsUnconfiguredWhenRequiredGoogleSettingsAreMissing() {
        assertFalse(client.isConfigured());
    }

    @Test
    void reportsConfiguredWhenRequiredGoogleSettingsExist() {
        configureGoogle();

        assertTrue(client.isConfigured());
    }

    @Test
    void mapsSdkResponseToCreatedMeeting() {
        MeetingIntegrationProperties.Google google = configureGoogle();
        SpacesServiceClient spacesClient = mock(SpacesServiceClient.class);
        when(googleMeetClientFactory.create(google)).thenReturn(spacesClient);
        when(spacesClient.createSpace(any(Space.class))).thenReturn(Space.newBuilder()
                .setName("spaces/abc")
                .setMeetingUri("https://meet.google.com/abc-defg-hij")
                .build());

        CreatedMeeting meeting = client.createMeeting(request());

        assertEquals("spaces/abc", meeting.externalMeetingId());
        assertEquals("https://meet.google.com/abc-defg-hij", meeting.joinUrl());
    }

    @Test
    void wrapsSdkFailuresAsMeetingProviderException() {
        MeetingIntegrationProperties.Google google = configureGoogle();
        when(googleMeetClientFactory.create(google)).thenThrow(new RuntimeException("Meet unavailable"));

        assertThrows(MeetingProviderException.class, () -> client.createMeeting(request()));
    }

    private MeetingIntegrationProperties.Google configureGoogle() {
        MeetingIntegrationProperties.Google google = properties.getProviders().getGoogle();
        google.setEnabled(true);
        google.setClientId("client-id");
        google.setClientSecret("client-secret");
        google.setRefreshToken("refresh-token");
        return google;
    }

    private CreateMeetingRequest request() {
        ZonedDateTime start = ZonedDateTime.of(2026, 9, 2, 10, 0, 0, 0, ZoneId.of("Africa/Lagos"));
        return new CreateMeetingRequest(
                MeetingProvider.GOOGLE_MEET,
                "Planning",
                "Agenda",
                start,
                start.plusMinutes(30),
                false,
                List.of("employee@company.com")
        );
    }
}
