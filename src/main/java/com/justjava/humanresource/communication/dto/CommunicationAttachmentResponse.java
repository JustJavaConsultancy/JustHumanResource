package com.justjava.humanresource.communication.dto;

import java.time.LocalDateTime;

public record CommunicationAttachmentResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long fileSize,
        LocalDateTime uploadedAt,
        String viewUrl,
        String downloadUrl
) {
}
