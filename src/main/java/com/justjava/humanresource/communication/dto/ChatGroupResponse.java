package com.justjava.humanresource.communication.dto;

import java.time.LocalDateTime;

public record ChatGroupResponse(
        Long id,
        String name,
        String description,
        String department,
        long memberCount,
        String createdByName,
        String createdByRole,
        String lastMessage,
        LocalDateTime lastMessageAt
) {
}
