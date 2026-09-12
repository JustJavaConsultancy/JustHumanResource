package com.justjava.humanresource.communication.meeting.provider;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MicrosoftTeamsMeetingProviderClientTest {

    private final MicrosoftGraphClientFactory graphClientFactory = mock(MicrosoftGraphClientFactory.class);
    private final MeetingIntegrationProperties properties = new MeetingIntegrationProperties();
    private final MicrosoftTeamsMeetingProviderClient client =
            new MicrosoftTeamsMeetingProviderClient(properties, graphClientFactory);

    @Test
    void reportsUnconfiguredWhenRequiredMicrosoftSettingsAreMissing() {
        assertFalse(client.isConfigured());
    }

    @Test
    void reportsConfiguredWhenRequiredMicrosoftSettingsExist() {
        configureMicrosoft();

        assertTrue(client.isConfigured());
    }

    @Test
    void wrapsGraphSdkFailuresAsMeetingProviderException() {
        MeetingIntegrationProperties.Microsoft microsoft = configureMicrosoft();
        when(graphClientFactory.create(microsoft)).thenThrow(new RuntimeException("Graph unavailable"));

        assertThrows(MeetingProviderException.class, () -> client.createMeeting(request()));
    }

    private MeetingIntegrationProperties.Microsoft configureMicrosoft() {
        MeetingIntegrationProperties.Microsoft microsoft = properties.getProviders().getMicrosoft();
        microsoft.setEnabled(true);
        microsoft.setTenantId("tenant-id");
        microsoft.setClientId("client-id");
        microsoft.setClientSecret("client-secret");
        microsoft.setDefaultOrganizerUserId("organizer@company.com");
        return microsoft;
    }

    private CreateMeetingRequest request() {
        ZonedDateTime start = ZonedDateTime.of(2026, 9, 2, 10, 0, 0, 0, ZoneId.of("Africa/Lagos"));
        return new CreateMeetingRequest(
                MeetingProvider.MICROSOFT_TEAMS,
                "Planning",
                "Agenda",
                start,
                start.plusMinutes(30),
                false,
                List.of("employee@company.com")
        );
    }
}
