package com.justjava.humanresource.communication.dto;

import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        EmployeeContactResponse otherParticipant,
        ChatMessageResponse lastMessage,
        LocalDateTime updatedAt
) {
}
