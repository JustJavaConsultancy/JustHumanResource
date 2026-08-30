package com.justjava.humanresource.communication.dto;

import java.time.LocalDateTime;

public record PresenceResponse(
        Long employeeId,
        String employeeNumber,
        String fullName,
        String department,
        boolean online,
        LocalDateTime lastSeenAt
) {
}
