package com.justjava.humanresource.communication.meeting.dto;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.entity.HrMeetingStatus;

import java.time.LocalDateTime;
import java.util.List;

public record HrMeetingResponse(
        Long id,
        MeetingProvider provider,
        String subject,
        String agenda,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean instantMeeting,
        String externalMeetingId,
        String joinUrl,
        String hostUrl,
        String createdByName,
        String createdByEmail,
        HrMeetingStatus status,
        List<HrMeetingParticipantResponse> participants
) {
}
