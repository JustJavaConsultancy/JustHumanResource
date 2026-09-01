package com.justjava.humanresource.communication.dto;

import java.time.LocalDateTime;

public record GroupMessageResponse(
        Long id,
        Long groupId,
        String groupName,
        Long senderId,
        String senderEmployeeNumber,
        String senderName,
        String content,
        LocalDateTime createdAt
) {
}
