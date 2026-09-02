package com.justjava.humanresource.communication.meeting.dto;

import com.justjava.humanresource.communication.meeting.MeetingProvider;

public record MeetingIntegrationStatusResponse(
        MeetingProvider provider,
        boolean enabled,
        boolean configured,
        String defaultOrganizer,
        String apiBaseUrl
) {
}
