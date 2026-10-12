package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.promotion.dto.PromotionAttachmentResponse;
import com.justjava.humanresource.promotion.entity.PromotionAttachment;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import com.justjava.humanresource.promotion.repository.PromotionAttachmentRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionAttachmentService;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import com.justjava.humanresource.promotion.service.PromotionDocumentIntegrationService;
import com.justjava.humanresource.promotion.service.PromotionLetterTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class PromotionAttachmentServiceTest {

    @TempDir Path tempDir;

    @Mock PromotionAttachmentRepository attachmentRepository;
    @Mock PromotionRequestRepository requestRepository;
    @Mock PromotionAuthorizationService authorizationService;
    @Mock PromotionCurrentUserService currentUserService;
    @Mock PromotionActivityService activityService;
    @Mock PromotionDocumentIntegrationService documentIntegrationService;

    private PromotionAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new PromotionAttachmentService(
                attachmentRepository,
                requestRepository,
                authorizationService,
                currentUserService,
                activityService,
                new PromotionLetterTemplateService(),
                documentIntegrationService
        );
        ReflectionTestUtils.setField(service, "storageRoot", tempDir.toString());
    }

    @Test
    void uploadsPromotionAttachment() {
        PromotionRequest request = request();
        Employee actor = employee(3L);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(actor);
        when(attachmentRepository.save(any(PromotionAttachment.class))).thenAnswer(invocation -> {
            PromotionAttachment attachment = invocation.getArgument(0);
            attachment.setId(50L);
            return attachment;
        });
        doAnswer(invocation -> {
            PromotionAttachment attachment = invocation.getArgument(0);
            attachment.setDocumentLibraryDocumentId(500L);
            return null;
        }).when(documentIntegrationService).syncToEmployeeDocumentLibrary(any(PromotionAttachment.class));
        MockMultipartFile file = new MockMultipartFile(
                "file", "summary.pdf", "application/pdf", "content".getBytes());

        PromotionAttachmentResponse response = service.upload(
                request.getId(), file, PromotionAttachmentType.APPRAISAL_SUMMARY, true);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getFileName()).isEqualTo("summary.pdf");
        assertThat(response.getDocumentType()).isEqualTo(PromotionAttachmentType.APPRAISAL_SUMMARY);
        assertThat(response.isRequired()).isTrue();
        assertThat(response.getGeneratedTemplateKey()).isNull();
        assertThat(response.getDocumentLibraryDocumentId()).isEqualTo(500L);
        assertThat(Files.exists(tempDir.resolve(String.valueOf(request.getId())))).isTrue();
        verify(authorizationService).requireHrOrAdmin();
        verify(documentIntegrationService).syncToEmployeeDocumentLibrary(any(PromotionAttachment.class));
        verify(activityService).record(any(), any(), any(), any());
    }

    @Test
    void rejectsUnsupportedFileType() {
        PromotionRequest request = request();
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/x-msdownload", "content".getBytes());

        assertThatThrownBy(() -> service.upload(request.getId(), file, PromotionAttachmentType.OTHER, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported file type: application/x-msdownload");
    }

    @Test
    void listsAttachmentsForViewableRequest() {
        PromotionRequest request = request();
        PromotionAttachment attachment = new PromotionAttachment();
        attachment.setId(10L);
        attachment.setPromotionRequest(request);
        attachment.setDocumentType(PromotionAttachmentType.OTHER);
        attachment.setFileName("note.txt");
        attachment.setContentType("text/plain");
        attachment.setFileSize(12L);
        attachment.setUploadedBy(employee(4L));
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(attachmentRepository.findByPromotionRequest_IdOrderByUploadedAtDesc(request.getId()))
                .thenReturn(List.of(attachment));

        List<PromotionAttachmentResponse> response = service.list(request.getId());

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getFileName()).isEqualTo("note.txt");
        assertThat(response.getFirst().getUploadedByName()).isEqualTo("First Last");
        verify(authorizationService).requireCanView(request);
    }

    @Test
    void generatesPromotionLetterAttachment() throws Exception {
        PromotionRequest request = detailedRequest();
        Employee actor = employee(3L);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(actor);
        when(attachmentRepository.save(any(PromotionAttachment.class))).thenAnswer(invocation -> {
            PromotionAttachment attachment = invocation.getArgument(0);
            attachment.setId(70L);
            return attachment;
        });
        doAnswer(invocation -> {
            PromotionAttachment attachment = invocation.getArgument(0);
            attachment.setDocumentLibraryDocumentId(700L);
            return null;
        }).when(documentIntegrationService).syncToEmployeeDocumentLibrary(any(PromotionAttachment.class));

        PromotionAttachmentResponse response = service.generateLetter(
                request.getId(), PromotionAttachmentType.PROMOTION_LETTER);

        assertThat(response.getId()).isEqualTo(70L);
        assertThat(response.getDocumentType()).isEqualTo(PromotionAttachmentType.PROMOTION_LETTER);
        assertThat(response.getFileName()).isEqualTo("PROM-2026-0001-promotion-letter.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getGeneratedTemplateKey()).isEqualTo("promotion-letter-standard");
        assertThat(response.getGeneratedTemplateName()).isEqualTo("Standard Promotion Letter");
        assertThat(response.getGeneratedTemplateVersion()).isEqualTo("v1");
        assertThat(response.getDocumentLibraryDocumentId()).isEqualTo(700L);
        Path generated = Files.list(tempDir.resolve(String.valueOf(request.getId())))
                .findFirst()
                .orElseThrow();
        String content;
        try (PDDocument document = PDDocument.load(generated.toFile())) {
            content = new PDFTextStripper().getText(document);
        }
        assertThat(content).contains("Promotion Letter", "Standard Promotion Letter", "First Last", "Senior Analyst");
        verify(authorizationService).requireHrOrAdmin();
        verify(activityService).record(any(), any(), any(), any());
    }

    @Test
    void generatesSalaryAdjustmentLetterWithSalaryTemplateMetadata() throws Exception {
        PromotionRequest request = detailedRequest();
        Employee actor = employee(3L);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(actor);
        when(attachmentRepository.save(any(PromotionAttachment.class))).thenAnswer(invocation -> {
            PromotionAttachment attachment = invocation.getArgument(0);
            attachment.setId(71L);
            return attachment;
        });
        doAnswer(invocation -> {
            PromotionAttachment attachment = invocation.getArgument(0);
            attachment.setDocumentLibrarySyncError("Document library unavailable");
            return null;
        }).when(documentIntegrationService).syncToEmployeeDocumentLibrary(any(PromotionAttachment.class));

        PromotionAttachmentResponse response = service.generateLetter(
                request.getId(), PromotionAttachmentType.SALARY_ADJUSTMENT_LETTER);

        assertThat(response.getId()).isEqualTo(71L);
        assertThat(response.getDocumentType()).isEqualTo(PromotionAttachmentType.SALARY_ADJUSTMENT_LETTER);
        assertThat(response.getFileName()).isEqualTo("PROM-2026-0001-salary-adjustment-letter.pdf");
        assertThat(response.getGeneratedTemplateKey()).isEqualTo("salary-adjustment-letter-standard");
        assertThat(response.getGeneratedTemplateName()).isEqualTo("Standard Salary Adjustment Letter");
        assertThat(response.getGeneratedTemplateVersion()).isEqualTo("v1");
        assertThat(response.getDocumentLibraryDocumentId()).isNull();
        assertThat(response.getDocumentLibrarySyncError()).isEqualTo("Document library unavailable");
        Path generated = Files.list(tempDir.resolve(String.valueOf(request.getId())))
                .findFirst()
                .orElseThrow();
        String content;
        try (PDDocument document = PDDocument.load(generated.toFile())) {
            content = new PDFTextStripper().getText(document);
        }
        assertThat(content).contains(
                "Salary Adjustment Letter",
                "Standard Salary Adjustment Letter",
                "Previous Salary: 100000.00",
                "New Salary: 125000.00"
        );
    }

    @Test
    void rejectsUnsupportedGeneratedLetterType() {
        PromotionRequest request = detailedRequest();
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.generateLetter(request.getId(), PromotionAttachmentType.OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only promotion and salary adjustment letters can be generated.");
    }

    private PromotionRequest request() {
        PromotionRequest request = new PromotionRequest();
        request.setId(20L);
        return request;
    }

    private PromotionRequest detailedRequest() {
        PromotionRequest request = request();
        request.setRequestNumber("PROM-2026-0001");
        request.setEmployee(employee(10L));
        JobStep currentRole = new JobStep();
        currentRole.setName("Analyst");
        JobStep proposedRole = new JobStep();
        proposedRole.setName("Senior Analyst");
        request.setCurrentRole(currentRole);
        request.setProposedRole(proposedRole);
        request.setEffectiveDate(LocalDate.of(2026, 11, 1));
        request.setCurrentSalary(new BigDecimal("100000.00"));
        request.setProposedSalary(new BigDecimal("125000.00"));
        request.setMonthlyPayrollImpact(new BigDecimal("25000.00"));
        request.setAnnualPayrollImpact(new BigDecimal("300000.00"));
        return request;
    }

    private Employee employee(Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFirstName("First");
        employee.setLastName("Last");
        return employee;
    }
}
