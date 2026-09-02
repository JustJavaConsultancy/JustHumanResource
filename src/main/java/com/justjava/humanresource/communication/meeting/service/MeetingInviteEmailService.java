package com.justjava.humanresource.communication.meeting.service;

import com.justjava.humanresource.communication.meeting.entity.HrMeeting;
import com.justjava.humanresource.communication.meeting.entity.HrMeetingParticipant;
import com.justjava.humanresource.utils.ResendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingInviteEmailService {

    private final ResendService resendService;
    private final MeetingInviteMessageBuilder messageBuilder;

    public String sendInvite(HrMeeting meeting, HrMeetingParticipant participant) {
        String email = participant.getEmployeeEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Participant has no email address.");
        }
        try {
            return resendService.sendEmail(
                    email.trim(),
                    "Meeting invite: " + meeting.getSubject(),
                    messageBuilder.html(meeting),
                    messageBuilder.text(meeting)
            );
        } catch (RuntimeException ex) {
            log.warn("Meeting invite email failed for meeting {} participant {}: {}",
                    meeting.getId(),
                    participant.getEmployee() == null ? null : participant.getEmployee().getId(),
                    ex.getMessage());
            throw ex;
        }
    }
}
