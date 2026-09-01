package com.justjava.humanresource.communication.dto;

import java.time.LocalDateTime;

public record EmployeeContactResponse(
        Long id,
        String employeeNumber,
        String fullName,
        String email,
        String department,
        boolean online,
        long unreadCount,
        Long conversationId,
        String lastMessage,
        LocalDateTime lastMessageAt,
        boolean hasConversation
) {
}
