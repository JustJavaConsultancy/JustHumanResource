package com.justjava.humanresource.communication.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderEmployeeNumber,
        String senderName,
        Long recipientId,
        String recipientEmployeeNumber,
        String content,
        LocalDateTime deliveredAt,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        List<CommunicationAttachmentResponse> attachments
) {
}
