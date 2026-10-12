package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.promotion.dto.PromotionDetailResponse;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionApprovalRouteService;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import com.justjava.humanresource.promotion.service.PromotionEligibilityService;
import com.justjava.humanresource.promotion.service.PromotionImplementationService;
import com.justjava.humanresource.promotion.service.PromotionMapper;
import com.justjava.humanresource.promotion.service.PromotionNotificationService;
import com.justjava.humanresource.promotion.service.PromotionRequiredDocumentService;
import com.justjava.humanresource.promotion.service.PromotionNumberService;
import com.justjava.humanresource.promotion.service.PromotionServiceImpl;
import com.justjava.humanresource.promotion.service.PromotionWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionSubmissionServiceTest {

    @Mock PromotionRequestRepository requestRepository;
    @Mock PromotionApprovalStepRepository approvalStepRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock JobStepRepository jobStepRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeAppraisalRepository appraisalRepository;
    @Mock PromotionNumberService numberService;
    @Mock PromotionEligibilityService eligibilityService;
    @Mock PromotionImplementationService implementationService;
    @Mock PromotionActivityService activityService;
    @Mock PromotionMapper mapper;
    @Mock PromotionCurrentUserService currentUserService;
    @Mock PromotionWorkflowService workflowService;
    @Mock PromotionApprovalRouteService approvalRouteService;
    @Mock PromotionAuthorizationService authorizationService;
    @Mock PromotionNotificationService notificationService;
    @Mock PromotionRequiredDocumentService requiredDocumentService;

    private PromotionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromotionServiceImpl(
                requestRepository,
                approvalStepRepository,
                employeeRepository,
                jobStepRepository,
                departmentRepository,
                appraisalRepository,
                numberService,
                eligibilityService,
                implementationService,
                activityService,
                mapper,
                currentUserService,
                workflowService,
                approvalRouteService,
                authorizationService,
                notificationService,
                requiredDocumentService
        );
    }

    @Test
    void submitEvaluatesEligibilityCreatesRouteStartsWorkflowAndMovesToFirstApprovalStage() {
        PromotionRequest request = new PromotionRequest();
        request.setId(50L);
        request.setRequestNumber("PR-2026-0001");
        request.setStatus(PromotionStatus.DRAFT);

        PromotionApprovalStep hrStep = new PromotionApprovalStep();
        hrStep.setPromotionRequest(request);
        hrStep.setStage(PromotionApprovalStage.HR);
        hrStep.setDecision(PromotionApprovalDecision.PENDING);

        PromotionDetailResponse response = PromotionDetailResponse.builder()
                .id(50L)
                .status(PromotionStatus.HR_REVIEW)
                .build();

        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(PromotionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalStepRepository.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                50L, PromotionApprovalDecision.PENDING))
                .thenReturn(Optional.of(hrStep), Optional.of(hrStep));
        when(workflowService.startApproval(request)).thenReturn("process-50");
        when(mapper.toDetail(request)).thenReturn(response);

        PromotionDetailResponse result = service.submit(50L);

        assertThat(result).isSameAs(response);
        assertThat(request.getStatus()).isEqualTo(PromotionStatus.HR_REVIEW);
        assertThat(request.getSubmittedAt()).isNotNull();
        assertThat(request.getProcessInstanceId()).isEqualTo("process-50");

        InOrder inOrder = inOrder(authorizationService, requiredDocumentService, eligibilityService, requestRepository,
                approvalRouteService, workflowService, activityService, notificationService);
        inOrder.verify(authorizationService).requireHrOrAdmin();
        inOrder.verify(requiredDocumentService).assertRequiredDocumentsPresent(
                request, PromotionRequiredDocumentService.EnforcementStage.SUBMIT);
        inOrder.verify(eligibilityService).assertCanSubmit(request);
        inOrder.verify(requestRepository).save(request);
        inOrder.verify(approvalRouteService).createApprovalRoute(request);
        inOrder.verify(workflowService).startApproval(request);
        inOrder.verify(activityService).record(request, PromotionActivityType.SUBMITTED, null,
                "Promotion request submitted for approval.");
        inOrder.verify(notificationService).submitted(request);
        inOrder.verify(notificationService).approvalAssigned(request, hrStep);
        verify(mapper).toDetail(request);
    }

    @Test
    void submitStopsBeforeEligibilityWhenRequiredDocumentsAreMissing() {
        PromotionRequest request = new PromotionRequest();
        request.setId(51L);
        request.setStatus(PromotionStatus.DRAFT);
        when(requestRepository.findById(51L)).thenReturn(Optional.of(request));
        org.mockito.Mockito.doThrow(new IllegalStateException("Promotion request is missing required documents: [APPRAISAL_SUMMARY]"))
                .when(requiredDocumentService)
                .assertRequiredDocumentsPresent(request, PromotionRequiredDocumentService.EnforcementStage.SUBMIT);

        assertThatThrownBy(() -> service.submit(51L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing required documents");

        verify(eligibilityService, never()).assertCanSubmit(request);
        verify(approvalRouteService, never()).createApprovalRoute(request);
        verify(workflowService, never()).startApproval(request);
    }
}

