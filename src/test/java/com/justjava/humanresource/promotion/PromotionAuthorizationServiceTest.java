package com.justjava.humanresource.promotion;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionAuthorizationServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock PromotionCurrentUserService currentUserService;
    @Mock PromotionApprovalStepRepository approvalStepRepository;

    private PromotionAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new PromotionAuthorizationService(authenticationManager, currentUserService, approvalStepRepository);
    }

    @Test
    void allowsHrAccessForHrUsers() {
        when(authenticationManager.isHumanResource()).thenReturn(true);

        service.requireHrOrAdmin();
    }

    @Test
    void rejectsHrAccessForNonHrUsers() {
        assertThatThrownBy(() -> service.requireHrOrAdmin())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("HR access is required.");
    }

    @Test
    void allowsAssignedLineManagerToViewPendingRequest() {
        Employee approver = employee(20L);
        PromotionRequest request = new PromotionRequest();
        request.setId(10L);
        PromotionApprovalStep step = new PromotionApprovalStep();
        step.setApprover(approver);
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(approver);
        when(approvalStepRepository.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                request.getId(), PromotionApprovalDecision.PENDING)).thenReturn(Optional.of(step));

        service.requireCanView(request);
    }

    @Test
    void allowsPromotedEmployeeToViewOwnPromotionHistory() {
        Employee employee = employee(20L);
        PromotionRequest request = new PromotionRequest();
        request.setEmployee(employee);
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(employee);

        service.requireCanView(request);
    }

    @Test
    void allowsRequesterToViewSubmittedPromotion() {
        Employee requester = employee(21L);
        PromotionRequest request = new PromotionRequest();
        request.setRequestedBy(requester);
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(requester);

        service.requireCanView(request);
    }

    @Test
    void rejectsUnassignedUsersFromViewingRequest() {
        PromotionRequest request = new PromotionRequest();
        request.setId(10L);
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(employee(30L));
        when(approvalStepRepository.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                request.getId(), PromotionApprovalDecision.PENDING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireCanView(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You cannot view this promotion request.");
    }

    @Test
    void buildsCandidateGroupsForAdmin() {
        when(authenticationManager.isAdmin()).thenReturn(true);

        assertThat(service.candidateGroupsForCurrentUser())
                .containsExactly("humanresource", "financialofficers", "executive");
    }

    @Test
    void allowsSalaryVisibilityForFinanceUsers() {
        when(authenticationManager.isFinancialOfficer()).thenReturn(true);

        assertThat(service.canViewSalary()).isTrue();
    }

    @Test
    void rejectsSalaryVisibilityForPlainAssignedApprover() {
        assertThat(service.canViewSalary()).isFalse();
    }

    @Test
    void requiresLineManagerTaskAssigneeMatch() {
        Task task = mock(Task.class);
        Employee actor = employee(20L);
        when(task.getAssignee()).thenReturn("30");

        assertThatThrownBy(() -> service.requireCanActOnTask(task, actor, PromotionApprovalStage.LINE_MANAGER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("This promotion approval is not assigned to you.");
    }

    @Test
    void rejectsSelfApproval() {
        assertThatThrownBy(() -> service.requireNotSelfApproval(employee(20L), "20"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You cannot approve your own promotion request.");
    }

    private Employee employee(Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        return employee;
    }
}
