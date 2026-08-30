package com.justjava.humanresource.communication.dto;

import java.time.LocalDateTime;

public record BroadcastCommentResponse(
        Long id,
        Long broadcastId,
        Long employeeId,
        String employeeName,
        String department,
        String content,
        LocalDateTime createdAt
) {
}
