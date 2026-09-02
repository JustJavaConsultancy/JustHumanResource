package com.justjava.humanresource.communication.meeting.service;

import com.justjava.humanresource.communication.dto.ChatMessageResponse;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import com.justjava.humanresource.communication.meeting.entity.HrMeeting;
import com.justjava.humanresource.communication.meeting.entity.HrMeetingParticipant;
import com.justjava.humanresource.communication.meeting.repository.HrMeetingParticipantRepository;
import com.justjava.humanresource.communication.service.CommunicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetingNotificationService {

    private final MeetingIntegrationProperties properties;
    private final CommunicationService communicationService;
    private final MeetingInviteEmailService emailService;
    private final MeetingInviteMessageBuilder messageBuilder;
    private final HrMeetingParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<HrMeetingParticipant> notifyParticipants(HrMeeting meeting, List<HrMeetingParticipant> participants) {
        return participants.stream()
                .map(participant -> notifyParticipant(meeting, participant))
                .toList();
    }

    private HrMeetingParticipant notifyParticipant(HrMeeting meeting, HrMeetingParticipant participant) {
        StringBuilder errors = new StringBuilder();
        if (properties.getNotifications().isSendChatInvites()) {
            sendChatInvite(meeting, participant, errors);
        }
        if (properties.getNotifications().isSendEmailInvites()) {
            sendEmailInvite(meeting, participant, errors);
        }
        participant.setNotified(participant.isChatNotified() || participant.isEmailNotified());
        participant.setNotifiedAt(participant.isNotified() ? LocalDateTime.now() : null);
        participant.setNotificationError(errors.isEmpty() ? null : errors.toString());
        return participantRepository.save(participant);
    }

    private void sendChatInvite(HrMeeting meeting, HrMeetingParticipant participant, StringBuilder errors) {
        Long senderId = properties.getNotifications().getSystemSenderEmployeeId();
        Long recipientId = participant.getEmployee() == null ? null : participant.getEmployee().getId();
        if (senderId == null) {
            markChatFailure(participant, errors, "Meeting chat sender is not configured");
            return;
        }
        if (recipientId == null) {
            markChatFailure(participant, errors, "Participant employee is missing");
            return;
        }
        try {
            ChatMessageResponse message = communicationService.sendSystemDirectMessageToEmployee(
                    senderId,
                    recipientId,
                    messageBuilder.text(meeting)
            );
            participant.setChatNotified(true);
            participant.setChatNotifiedAt(LocalDateTime.now());
            participant.setChatStatus("SENT");
            messagingTemplate.convertAndSendToUser(message.recipientEmployeeNumber(), "/queue/messages", message);
            messagingTemplate.convertAndSendToUser(message.senderEmployeeNumber(), "/queue/messages", message);
        } catch (RuntimeException ex) {
            markChatFailure(participant, errors, ex.getMessage());
        }
    }

    private void sendEmailInvite(HrMeeting meeting, HrMeetingParticipant participant, StringBuilder errors) {
        if (participant.getEmployeeEmail() == null || participant.getEmployeeEmail().isBlank()) {
            markEmailFailure(participant, errors, "Participant has no email address");
            return;
        }
        try {
            emailService.sendInvite(meeting, participant);
            participant.setEmailNotified(true);
            participant.setEmailNotifiedAt(LocalDateTime.now());
            participant.setEmailStatus("SENT");
        } catch (RuntimeException ex) {
            markEmailFailure(participant, errors, ex.getMessage());
        }
    }

    private void markChatFailure(HrMeetingParticipant participant, StringBuilder errors, String message) {
        participant.setChatStatus("FAILED");
        appendError(errors, "Chat: " + message);
    }

    private void markEmailFailure(HrMeetingParticipant participant, StringBuilder errors, String message) {
        participant.setEmailStatus("FAILED");
        appendError(errors, "Email: " + message);
    }

    private void appendError(StringBuilder errors, String message) {
        if (!errors.isEmpty()) {
            errors.append("; ");
        }
        errors.append(message == null || message.isBlank() ? "Unknown error" : message);
    }
}
