package com.justjava.humanresource.communication.meeting.dto;

public record HrMeetingParticipantResponse(
        Long employeeId,
        String employeeName,
        String employeeEmail,
        String employeeNumber,
        boolean notified,
        boolean chatNotified,
        String chatStatus,
        boolean emailNotified,
        String emailStatus,
        String notificationError
) {
}
