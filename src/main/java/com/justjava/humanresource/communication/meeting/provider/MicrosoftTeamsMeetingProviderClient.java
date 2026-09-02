package com.justjava.humanresource.communication.meeting.provider;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import com.microsoft.graph.models.OnlineMeeting;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicrosoftTeamsMeetingProviderClient implements MeetingProviderClient {

    private final MeetingIntegrationProperties properties;
    private final MicrosoftGraphClientFactory graphClientFactory;

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
        OnlineMeeting meeting = new OnlineMeeting();
        meeting.setSubject(request.subject());
        meeting.setStartDateTime(request.startTime().toOffsetDateTime());
        meeting.setEndDateTime(request.endTime().toOffsetDateTime());

        try {
            GraphServiceClient graphClient = graphClientFactory.create(microsoft);
            OnlineMeeting response = graphClient
                    .users()
                    .byUserId(microsoft.getDefaultOrganizerUserId())
                    .onlineMeetings()
                    .post(meeting);
            return new CreatedMeeting(
                    response == null ? null : response.getId(),
                    response == null ? null : response.getJoinWebUrl(),
                    null,
                    "Microsoft Teams meeting created"
            );
        } catch (RuntimeException ex) {
            throw new MeetingProviderException("Microsoft Teams meeting creation failed", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
