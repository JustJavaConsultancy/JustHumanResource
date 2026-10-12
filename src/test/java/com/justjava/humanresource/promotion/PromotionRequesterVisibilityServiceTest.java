package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.promotion.dto.PromotionDashboardResponse;
import com.justjava.humanresource.promotion.dto.PromotionSummaryResponse;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
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
import com.justjava.humanresource.promotion.service.PromotionNumberService;
import com.justjava.humanresource.promotion.service.PromotionRequiredDocumentService;
import com.justjava.humanresource.promotion.service.PromotionServiceImpl;
import com.justjava.humanresource.promotion.service.PromotionWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionRequesterVisibilityServiceTest {

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
    void myPromotionsReturnsCurrentEmployeeAndRequesterRecordsOnly() {
        Employee current = employee(10L);
        PromotionRequest draft = request(1L, PromotionStatus.DRAFT, LocalDateTime.now().minusDays(2));
        PromotionRequest approved = request(2L, PromotionStatus.APPROVED, LocalDateTime.now().minusDays(1));
        when(currentUserService.findCurrentEmployee()).thenReturn(Optional.of(current));
        when(requestRepository.findByEmployee_IdOrRequestedBy_Id(10L, 10L)).thenReturn(List.of(draft, approved));
        when(mapper.toSummary(approved)).thenReturn(summary(2L));

        List<PromotionSummaryResponse> response = service.myPromotions(PromotionStatus.APPROVED);

        assertThat(response).extracting(PromotionSummaryResponse::getId).containsExactly(2L);
        verify(requestRepository).findByEmployee_IdOrRequestedBy_Id(10L, 10L);
    }

    @Test
    void myDashboardCountsOnlyCurrentEmployeeAndRequesterRecords() {
        Employee current = employee(10L);
        PromotionRequest pending = request(1L, PromotionStatus.HR_REVIEW, LocalDateTime.now().minusDays(1));
        PromotionRequest approved = request(2L, PromotionStatus.APPROVED, LocalDateTime.now().minusDays(2));
        approved.setMonthlyPayrollImpact(new BigDecimal("25000.00"));
        approved.setAnnualPayrollImpact(new BigDecimal("300000.00"));
        PromotionRequest implemented = request(3L, PromotionStatus.IMPLEMENTED, LocalDateTime.now().minusDays(3));
        implemented.setMonthlyPayrollImpact(new BigDecimal("10000.00"));
        implemented.setAnnualPayrollImpact(new BigDecimal("120000.00"));
        when(currentUserService.findCurrentEmployee()).thenReturn(Optional.of(current));
        when(requestRepository.findByEmployee_IdOrRequestedBy_Id(10L, 10L))
                .thenReturn(List.of(pending, approved, implemented));
        when(mapper.toSummary(pending)).thenReturn(summary(1L));
        when(mapper.toSummary(approved)).thenReturn(summary(2L));
        when(mapper.toSummary(implemented)).thenReturn(summary(3L));

        PromotionDashboardResponse response = service.myDashboard();

        assertThat(response.getPendingApprovalCount()).isEqualTo(1);
        assertThat(response.getApprovedCount()).isEqualTo(1);
        assertThat(response.getImplementedCount()).isEqualTo(1);
        assertThat(response.getMonthlySalaryImpact()).isEqualByComparingTo("35000.00");
        assertThat(response.getAnnualSalaryImpact()).isEqualByComparingTo("420000.00");
        assertThat(response.getRecentRequests()).extracting(PromotionSummaryResponse::getId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void myPromotionsRequiresEmployeeContext() {
        when(currentUserService.findCurrentEmployee()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myPromotions(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Employee context is required.");
    }

    private Employee employee(Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        return employee;
    }

    private PromotionRequest request(Long id, PromotionStatus status, LocalDateTime requestedAt) {
        PromotionRequest request = new PromotionRequest();
        request.setId(id);
        request.setStatus(status);
        request.setRequestedAt(requestedAt);
        return request;
    }

    private PromotionSummaryResponse summary(Long id) {
        return PromotionSummaryResponse.builder()
                .id(id)
                .build();
    }
}
