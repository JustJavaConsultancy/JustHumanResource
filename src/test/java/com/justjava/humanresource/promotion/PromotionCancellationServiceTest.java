package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.promotion.dto.PromotionDecisionCommand;
import com.justjava.humanresource.promotion.dto.PromotionDetailResponse;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionCancellationServiceTest {

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
    void cancelsDraftRequestAndRecordsActivity() {
        PromotionRequest request = request(PromotionStatus.DRAFT);
        Employee actor = new Employee();
        actor.setId(9L);
        PromotionDecisionCommand command = new PromotionDecisionCommand();
        command.setComment("Duplicate request");
        PromotionDetailResponse detail = PromotionDetailResponse.builder()
                .id(request.getId())
                .status(PromotionStatus.CANCELLED)
                .build();
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(actor);
        when(requestRepository.save(request)).thenReturn(request);
        when(mapper.toDetail(request)).thenReturn(detail);

        PromotionDetailResponse response = service.cancel(request.getId(), command);

        assertThat(response.getStatus()).isEqualTo(PromotionStatus.CANCELLED);
        assertThat(request.getStatus()).isEqualTo(PromotionStatus.CANCELLED);
        assertThat(request.getCancelledAt()).isNotNull();
        assertThat(request.getLastApprovalComment()).isEqualTo("Duplicate request");
        verify(authorizationService).requireHrOrAdmin();
        verify(workflowService).cancelApproval(request, "Duplicate request");
        verify(activityService).record(request, PromotionActivityType.CANCELLED, actor,
                "Promotion request cancelled: Duplicate request");
        verify(notificationService).cancelled(request, actor);
    }

    @Test
    void rejectsCancellationForApprovedRequest() {
        PromotionRequest request = request(PromotionStatus.APPROVED);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(request.getId(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Promotion request cannot be cancelled from status APPROVED.");

        verify(workflowService, never()).cancelApproval(request, null);
        verify(requestRepository, never()).save(request);
    }

    private PromotionRequest request(PromotionStatus status) {
        PromotionRequest request = new PromotionRequest();
        request.setId(30L);
        request.setStatus(status);
        return request;
    }
}

