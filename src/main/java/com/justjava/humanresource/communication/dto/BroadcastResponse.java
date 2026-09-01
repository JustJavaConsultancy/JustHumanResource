package com.justjava.humanresource.communication.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BroadcastResponse(
        Long id,
        String title,
        String content,
        String createdByName,
        String createdByEmail,
        String status,
        LocalDateTime createdAt,
        long deliveredCount,
        long readCount,
        long commentCount,
        boolean read,
        List<CommunicationAttachmentResponse> attachments
) {
}
