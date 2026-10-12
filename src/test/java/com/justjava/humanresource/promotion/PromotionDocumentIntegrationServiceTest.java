package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeeDocument;
import com.justjava.humanresource.hr.repository.EmployeeDocumentRepository;
import com.justjava.humanresource.promotion.entity.PromotionAttachment;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import com.justjava.humanresource.promotion.service.PromotionDocumentIntegrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionDocumentIntegrationServiceTest {

    @TempDir Path tempDir;

    @Mock EmployeeDocumentRepository employeeDocumentRepository;

    @Test
    void syncsPromotionAttachmentToEmployeeDocumentLibrary() throws Exception {
        Path file = tempDir.resolve("letter.pdf");
        Files.writeString(file, "pdf bytes");
        PromotionDocumentIntegrationService service = new PromotionDocumentIntegrationService(employeeDocumentRepository);
        when(employeeDocumentRepository.save(any(EmployeeDocument.class))).thenAnswer(invocation -> {
            EmployeeDocument document = invocation.getArgument(0);
            document.setId(80L);
            return document;
        });
        PromotionAttachment attachment = attachment(file);

        service.syncToEmployeeDocumentLibrary(attachment);

        ArgumentCaptor<EmployeeDocument> captor = ArgumentCaptor.forClass(EmployeeDocument.class);
        verify(employeeDocumentRepository).save(captor.capture());
        EmployeeDocument document = captor.getValue();
        assertThat(document.getEmployee().getId()).isEqualTo(10L);
        assertThat(document.getDocumentName()).isEqualTo("PROM-2026-0001 - PROMOTION LETTER");
        assertThat(document.getFileName()).isEqualTo("promotion-letter.pdf");
        assertThat(document.getFileType()).isEqualTo("application/pdf");
        assertThat(document.getFileData()).isEqualTo("pdf bytes".getBytes());
        assertThat(document.getUploadedBy()).isEqualTo("First Last");
        assertThat(attachment.getDocumentLibraryDocumentId()).isEqualTo(80L);
        assertThat(attachment.getDocumentLibrarySyncedAt()).isNotNull();
        assertThat(attachment.getDocumentLibrarySyncError()).isNull();
    }

    @Test
    void skipsAlreadySyncedAttachmentForRetrySafety() throws Exception {
        Path file = tempDir.resolve("letter.pdf");
        Files.writeString(file, "pdf bytes");
        PromotionDocumentIntegrationService service = new PromotionDocumentIntegrationService(employeeDocumentRepository);
        PromotionAttachment attachment = attachment(file);
        attachment.setDocumentLibraryDocumentId(80L);

        service.syncToEmployeeDocumentLibrary(attachment);

        verify(employeeDocumentRepository, never()).save(any());
    }

    @Test
    void recordsSyncErrorWithoutThrowing() {
        PromotionDocumentIntegrationService service = new PromotionDocumentIntegrationService(employeeDocumentRepository);
        PromotionAttachment attachment = attachment(tempDir.resolve("missing.pdf"));

        service.syncToEmployeeDocumentLibrary(attachment);

        assertThat(attachment.getDocumentLibraryDocumentId()).isNull();
        assertThat(attachment.getDocumentLibrarySyncError()).contains("missing.pdf");
    }

    private PromotionAttachment attachment(Path file) {
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setFirstName("Target");
        employee.setLastName("Employee");

        Employee uploadedBy = new Employee();
        uploadedBy.setId(11L);
        uploadedBy.setFirstName("First");
        uploadedBy.setLastName("Last");

        PromotionRequest request = new PromotionRequest();
        request.setId(20L);
        request.setRequestNumber("PROM-2026-0001");
        request.setEmployee(employee);

        PromotionAttachment attachment = new PromotionAttachment();
        attachment.setId(30L);
        attachment.setPromotionRequest(request);
        attachment.setDocumentType(PromotionAttachmentType.PROMOTION_LETTER);
        attachment.setFileName("promotion-letter.pdf");
        attachment.setContentType("application/pdf");
        attachment.setStorageReference(file.toString());
        attachment.setUploadedBy(uploadedBy);
        attachment.setUploadedAt(LocalDateTime.of(2026, 10, 11, 10, 0));
        return attachment;
    }
}
