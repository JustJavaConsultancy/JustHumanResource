package com.justjava.humanresource.documents;

import java.time.LocalDateTime;

public record EmployeeDocumentLibraryRow(
        Long id,
        String documentName,
        String fileName,
        String fileType,
        LocalDateTime uploadedAt,
        String uploadedBy,
        Long employeeId,
        String employeeName,
        String employeeNumber,
        Long jobGradeId
) {
}
