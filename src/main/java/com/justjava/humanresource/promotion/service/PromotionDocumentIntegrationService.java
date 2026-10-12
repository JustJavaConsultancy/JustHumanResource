package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeeDocument;
import com.justjava.humanresource.hr.repository.EmployeeDocumentRepository;
import com.justjava.humanresource.promotion.entity.PromotionAttachment;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PromotionDocumentIntegrationService {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final EmployeeDocumentRepository employeeDocumentRepository;

    public void syncToEmployeeDocumentLibrary(PromotionAttachment attachment) {
        if (attachment == null || attachment.getDocumentLibraryDocumentId() != null) {
            return;
        }
        try {
            PromotionRequest request = attachment.getPromotionRequest();
            Employee employee = request == null ? null : request.getEmployee();
            if (employee == null || employee.getId() == null) {
                throw new IllegalStateException("Cannot sync promotion document without a target employee.");
            }

            EmployeeDocument document = EmployeeDocument.builder()
                    .employee(employee)
                    .documentName(documentName(attachment))
                    .fileName(attachment.getFileName())
                    .fileType(attachment.getContentType())
                    .fileData(readFile(attachment))
                    .uploadedAt(attachment.getUploadedAt() == null ? LocalDateTime.now() : attachment.getUploadedAt())
                    .uploadedBy(uploadedByName(attachment))
                    .build();

            EmployeeDocument saved = employeeDocumentRepository.save(document);
            attachment.setDocumentLibraryDocumentId(saved.getId());
            attachment.setDocumentLibrarySyncedAt(LocalDateTime.now());
            attachment.setDocumentLibrarySyncError(null);
        } catch (RuntimeException | IOException ex) {
            attachment.setDocumentLibrarySyncError(truncate(ex.getMessage()));
        }
    }

    private byte[] readFile(PromotionAttachment attachment) throws IOException {
        return Files.readAllBytes(Path.of(attachment.getStorageReference()).normalize());
    }

    private String documentName(PromotionAttachment attachment) {
        PromotionRequest request = attachment.getPromotionRequest();
        String requestNumber = request == null || request.getRequestNumber() == null
                ? "Promotion"
                : request.getRequestNumber();
        String type = attachment.getDocumentType() == null
                ? "Document"
                : attachment.getDocumentType().name().replace('_', ' ');
        return requestNumber + " - " + type;
    }

    private String uploadedByName(PromotionAttachment attachment) {
        Employee uploadedBy = attachment.getUploadedBy();
        return uploadedBy == null ? "System" : uploadedBy.getFullName();
    }

    private String truncate(String value) {
        String message = value == null || value.isBlank() ? "Document library sync failed." : value;
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
