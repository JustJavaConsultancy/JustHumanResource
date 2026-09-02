package com.justjava.humanresource.communication.meeting.provider;

public record CreatedMeeting(
        String externalMeetingId,
        String joinUrl,
        String hostUrl,
        String providerResponseSummary
) {
}
