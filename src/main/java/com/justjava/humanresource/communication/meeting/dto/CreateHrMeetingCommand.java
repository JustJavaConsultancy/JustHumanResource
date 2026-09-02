package com.justjava.humanresource.communication.meeting.dto;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CreateHrMeetingCommand(
        MeetingProvider provider,
        @NotBlank @Size(max = 200) String subject,
        @Size(max = 2000) String agenda,
        boolean instantMeeting,
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<Long> participantEmployeeIds,
        boolean notifyParticipants
) {
}
