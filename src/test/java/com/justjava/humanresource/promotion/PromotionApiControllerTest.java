package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.promotion.dto.PromotionReassignmentCommand;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.service.PromotionAttachmentService;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionApprovalReassignmentService;
import com.justjava.humanresource.promotion.service.PromotionConfigurationService;
import com.justjava.humanresource.promotion.service.PromotionEligibilityService;
import com.justjava.humanresource.promotion.service.PromotionPayrollHandoffService;
import com.justjava.humanresource.promotion.service.PromotionReportService;
import com.justjava.humanresource.promotion.service.PromotionService;
import com.justjava.humanresource.promotion.service.PromotionWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionApiControllerTest {

    @Mock PromotionService promotionService;
    @Mock PromotionWorkflowService promotionWorkflowService;
    @Mock PromotionAttachmentService promotionAttachmentService;
    @Mock PromotionConfigurationService promotionConfigurationService;
    @Mock PromotionEligibilityService promotionEligibilityService;
    @Mock PromotionReportService promotionReportService;
    @Mock PromotionPayrollHandoffService promotionPayrollHandoffService;
    @Mock PromotionAuthorizationService promotionAuthorizationService;
    @Mock PromotionApprovalReassignmentService promotionApprovalReassignmentService;
    @Mock EmployeeRepository employeeRepository;
    @Mock JobStepRepository jobStepRepository;

    private PromotionApiController controller;

    @BeforeEach
    void setUp() {
        controller = new PromotionApiController(
                promotionService,
                promotionWorkflowService,
                promotionAttachmentService,
                promotionConfigurationService,
                promotionEligibilityService,
                promotionReportService,
                promotionPayrollHandoffService,
                promotionAuthorizationService,
                promotionApprovalReassignmentService,
                employeeRepository,
                jobStepRepository
        );
    }

    @Test
    void employeeReferenceRequiresHrFinanceOrAdminAccess() {
        when(employeeRepository.findAllVisible()).thenReturn(List.of());

        controller.employees();

        verify(promotionAuthorizationService).requireHrFinanceOrAdmin();
    }

    @Test
    void jobStepReferenceRequiresHrFinanceOrAdminAccess() {
        when(jobStepRepository.findAllWithJobGrade()).thenReturn(List.of());

        controller.jobSteps();

        verify(promotionAuthorizationService).requireHrFinanceOrAdmin();
    }

    @Test
    void myPromotionsDelegatesToScopedServiceList() {
        controller.myPromotions(PromotionStatus.APPROVED);

        verify(promotionService).myPromotions(PromotionStatus.APPROVED);
    }

    @Test
    void myDashboardDelegatesToScopedServiceDashboard() {
        controller.myDashboard();

        verify(promotionService).myDashboard();
    }

    @Test
    void reportDetailsDelegatesToReportService() {
        controller.reportDetails(
                PromotionStatus.APPROVED,
                12L,
                java.time.LocalDate.of(2026, 1, 1),
                java.time.LocalDate.of(2026, 1, 31),
                java.time.LocalDate.of(2026, 2, 1),
                java.time.LocalDate.of(2026, 2, 28),
                2,
                25);

        verify(promotionReportService).details(
                PromotionStatus.APPROVED,
                12L,
                java.time.LocalDate.of(2026, 1, 1),
                java.time.LocalDate.of(2026, 1, 31),
                java.time.LocalDate.of(2026, 2, 1),
                java.time.LocalDate.of(2026, 2, 28),
                2,
                25);
    }

    @Test
    void reassignApprovalDelegatesToReassignmentService() {
        PromotionReassignmentCommand command = new PromotionReassignmentCommand();

        controller.reassignApproval(15L, 22L, command);

        verify(promotionApprovalReassignmentService).reassign(15L, 22L, command);
    }

    @Test
    void delegateApprovalDelegatesToReassignmentService() {
        PromotionReassignmentCommand command = new PromotionReassignmentCommand();

        controller.delegateApproval(15L, 22L, command);

        verify(promotionApprovalReassignmentService).delegate(15L, 22L, command);
    }
}
