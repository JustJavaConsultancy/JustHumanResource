package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.dto.PromotionDetailResponse;
import com.justjava.humanresource.promotion.dto.PromotionSummaryResponse;
import com.justjava.humanresource.promotion.entity.PromotionActivityLog;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.repository.PromotionActivityLogRepository;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionAttachmentRepository;
import com.justjava.humanresource.promotion.repository.PromotionEligibilityCheckRepository;
import com.justjava.humanresource.promotion.repository.PromotionPayrollHandoffRepository;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionMapper;
import com.justjava.humanresource.promotion.service.PromotionRequiredDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionMapperTest {

    @Mock PromotionEligibilityCheckRepository eligibilityCheckRepository;
    @Mock PromotionApprovalStepRepository approvalStepRepository;
    @Mock PromotionAttachmentRepository attachmentRepository;
    @Mock PromotionActivityLogRepository activityLogRepository;
    @Mock PromotionPayrollHandoffRepository payrollHandoffRepository;
    @Mock PromotionAuthorizationService authorizationService;
    @Mock PromotionRequiredDocumentService requiredDocumentService;

    private PromotionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PromotionMapper(
                eligibilityCheckRepository,
                approvalStepRepository,
                attachmentRepository,
                activityLogRepository,
                payrollHandoffRepository,
                authorizationService,
                requiredDocumentService
        );
    }

    @Test
    void hidesSalaryFieldsWhenCurrentUserCannotViewSalary() {
        PromotionRequest request = salaryRequest();
        when(authorizationService.canViewSalary()).thenReturn(false);
        when(eligibilityCheckRepository.findByPromotionRequest_IdOrderByIdAsc(request.getId())).thenReturn(List.of());
        when(approvalStepRepository.findByPromotionRequest_IdOrderBySequenceNumberAsc(request.getId())).thenReturn(List.of());
        when(attachmentRepository.findByPromotionRequest_IdOrderByUploadedAtDesc(request.getId())).thenReturn(List.of());
        when(activityLogRepository.findByPromotionRequest_IdOrderByCreatedAtDesc(request.getId())).thenReturn(List.of());

        PromotionDetailResponse detail = mapper.toDetail(request);
        PromotionSummaryResponse summary = mapper.toSummary(request);

        assertThat(detail.getCurrentSalary()).isNull();
        assertThat(detail.getProposedSalary()).isNull();
        assertThat(detail.getSalaryIncreaseAmount()).isNull();
        assertThat(detail.getMonthlyPayrollImpact()).isNull();
        assertThat(detail.getPayrollHandoff()).isNull();
        assertThat(summary.getSalaryIncreaseAmount()).isNull();
        assertThat(summary.getSalaryIncreasePercent()).isNull();
        verify(payrollHandoffRepository, never()).findByPromotionRequest_Id(request.getId());
    }

    @Test
    void includesSalaryFieldsWhenCurrentUserCanViewSalary() {
        PromotionRequest request = salaryRequest();
        when(authorizationService.canViewSalary()).thenReturn(true);
        when(eligibilityCheckRepository.findByPromotionRequest_IdOrderByIdAsc(request.getId())).thenReturn(List.of());
        when(approvalStepRepository.findByPromotionRequest_IdOrderBySequenceNumberAsc(request.getId())).thenReturn(List.of());
        when(attachmentRepository.findByPromotionRequest_IdOrderByUploadedAtDesc(request.getId())).thenReturn(List.of());
        when(activityLogRepository.findByPromotionRequest_IdOrderByCreatedAtDesc(request.getId())).thenReturn(List.of());
        when(payrollHandoffRepository.findByPromotionRequest_Id(request.getId())).thenReturn(java.util.Optional.empty());

        PromotionDetailResponse detail = mapper.toDetail(request);
        PromotionSummaryResponse summary = mapper.toSummary(request);

        assertThat(detail.getCurrentSalary()).isEqualByComparingTo("100000.00");
        assertThat(detail.getProposedSalary()).isEqualByComparingTo("125000.00");
        assertThat(detail.getSalaryIncreaseAmount()).isEqualByComparingTo("25000.00");
        assertThat(detail.getMonthlyPayrollImpact()).isEqualByComparingTo("25000.00");
        assertThat(summary.getSalaryIncreaseAmount()).isEqualByComparingTo("25000.00");
        assertThat(summary.getSalaryIncreasePercent()).isEqualByComparingTo("25.00");
    }

    @Test
    void includesMissingRequiredDocumentsInDetailResponse() {
        PromotionRequest request = salaryRequest();
        when(authorizationService.canViewSalary()).thenReturn(false);
        when(eligibilityCheckRepository.findByPromotionRequest_IdOrderByIdAsc(request.getId())).thenReturn(List.of());
        when(approvalStepRepository.findByPromotionRequest_IdOrderBySequenceNumberAsc(request.getId())).thenReturn(List.of());
        when(attachmentRepository.findByPromotionRequest_IdOrderByUploadedAtDesc(request.getId())).thenReturn(List.of());
        when(activityLogRepository.findByPromotionRequest_IdOrderByCreatedAtDesc(request.getId())).thenReturn(List.of());
        when(requiredDocumentService.missingRequiredDocuments(
                request, PromotionRequiredDocumentService.EnforcementStage.SUBMIT))
                .thenReturn(List.of(PromotionAttachmentType.APPRAISAL_SUMMARY));
        when(requiredDocumentService.missingRequiredDocuments(
                request, PromotionRequiredDocumentService.EnforcementStage.IMPLEMENTATION))
                .thenReturn(List.of(PromotionAttachmentType.PROMOTION_LETTER));

        PromotionDetailResponse detail = mapper.toDetail(request);

        assertThat(detail.getMissingRequiredDocumentsBeforeSubmit())
                .containsExactly(PromotionAttachmentType.APPRAISAL_SUMMARY);
        assertThat(detail.getMissingRequiredDocumentsBeforeImplementation())
                .containsExactly(PromotionAttachmentType.PROMOTION_LETTER);
    }

    @Test
    void includesActivityAuditMetadataInDetailResponse() {
        PromotionRequest request = salaryRequest();
        PromotionActivityLog activity = new PromotionActivityLog();
        activity.setId(99L);
        activity.setPromotionRequest(request);
        activity.setActivityType(PromotionActivityType.CREATED);
        activity.setDescription("Created.");
        activity.setIpAddress("203.0.113.10");
        activity.setUserAgent("Mozilla/5.0");
        activity.setDeviceMetadata("Desktop");
        when(authorizationService.canViewSalary()).thenReturn(false);
        when(eligibilityCheckRepository.findByPromotionRequest_IdOrderByIdAsc(request.getId())).thenReturn(List.of());
        when(approvalStepRepository.findByPromotionRequest_IdOrderBySequenceNumberAsc(request.getId())).thenReturn(List.of());
        when(attachmentRepository.findByPromotionRequest_IdOrderByUploadedAtDesc(request.getId())).thenReturn(List.of());
        when(activityLogRepository.findByPromotionRequest_IdOrderByCreatedAtDesc(request.getId())).thenReturn(List.of(activity));

        PromotionDetailResponse detail = mapper.toDetail(request);

        assertThat(detail.getActivities()).hasSize(1);
        assertThat(detail.getActivities().getFirst().getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(detail.getActivities().getFirst().getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(detail.getActivities().getFirst().getDeviceMetadata()).isEqualTo("Desktop");
    }

    private PromotionRequest salaryRequest() {
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setFirstName("First");
        employee.setLastName("Last");

        PromotionRequest request = new PromotionRequest();
        request.setId(20L);
        request.setEmployee(employee);
        request.setCurrentSalary(new BigDecimal("100000.00"));
        request.setProposedSalary(new BigDecimal("125000.00"));
        request.setSalaryIncreaseAmount(new BigDecimal("25000.00"));
        request.setSalaryIncreasePercent(new BigDecimal("25.00"));
        request.setMonthlyPayrollImpact(new BigDecimal("25000.00"));
        request.setAnnualPayrollImpact(new BigDecimal("300000.00"));
        return request;
    }
}

