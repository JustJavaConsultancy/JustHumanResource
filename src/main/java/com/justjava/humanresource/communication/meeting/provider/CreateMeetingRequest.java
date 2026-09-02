package com.justjava.humanresource.communication.meeting.provider;

import com.justjava.humanresource.communication.meeting.MeetingProvider;

import java.time.ZonedDateTime;
import java.util.List;

public record CreateMeetingRequest(
        MeetingProvider provider,
        String subject,
        String agenda,
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        boolean instantMeeting,
        List<String> participantEmails
) {
    public long durationMinutes() {
        return Math.max(1, java.time.Duration.between(startTime, endTime).toMinutes());
    }
}
