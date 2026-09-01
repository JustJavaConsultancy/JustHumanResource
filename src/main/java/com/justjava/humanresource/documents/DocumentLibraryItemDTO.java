package com.justjava.humanresource.documents;

import java.time.LocalDateTime;

public record DocumentLibraryItemDTO(
        Long id,
        String sourceType,
        String displayName,
        String originalFileName,
        String contentType,
        Long fileSize,
        LocalDateTime uploadedAt,
        String uploadedBy,
        Long ownerEmployeeId,
        String ownerEmployeeName,
        String employeeNumber,
        Long requestId,
        String requestNumber,
        String requestType,
        String requestStatus,
        String documentName,
        String attachmentType,
        String description,
        String viewUrl,
        String downloadUrl
) {
}
