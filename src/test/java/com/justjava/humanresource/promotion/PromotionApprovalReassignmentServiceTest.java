package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.dto.PromotionReassignmentCommand;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionApprovalReassignmentService;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import com.justjava.humanresource.promotion.service.PromotionMapper;
import com.justjava.humanresource.promotion.service.PromotionNotificationService;
import com.justjava.humanresource.promotion.service.PromotionWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionApprovalReassignmentServiceTest {

    @Mock PromotionRequestRepository requests;
    @Mock PromotionApprovalStepRepository steps;
    @Mock EmployeeRepository employees;
    @Mock PromotionAuthorizationService authorization;
    @Mock PromotionCurrentUserService currentUser;
    @Mock PromotionWorkflowService workflow;
    @Mock PromotionActivityService activities;
    @Mock PromotionNotificationService notifications;
    @Mock PromotionMapper mapper;

    private PromotionApprovalReassignmentService service;
    private PromotionRequest request;
    private PromotionApprovalStep step;
    private Employee actor;
    private Employee approver;

    @BeforeEach
    void setUp() {
        service = new PromotionApprovalReassignmentService(
                requests,
                steps,
                employees,
                authorization,
                currentUser,
                workflow,
                activities,
                notifications,
                mapper
        );
        request = new PromotionRequest();
        request.setId(12L);
        step = new PromotionApprovalStep();
        step.setId(33L);
        step.setPromotionRequest(request);
        step.setStage(PromotionApprovalStage.HR);
        step.setDecision(PromotionApprovalDecision.PENDING);
        step.setApproverGroup("humanresource");
        actor = employee(7L, "Ada Admin");
        approver = employee(9L, "Ben Approver");
    }

    @Test
    void reassignsActivePendingStepAndFlowableTask() {
        PromotionReassignmentCommand command = new PromotionReassignmentCommand();
        command.setApproverId(9L);
        command.setComment("Covering the approval queue");
        when(requests.findById(12L)).thenReturn(Optional.of(request));
        when(steps.findById(33L)).thenReturn(Optional.of(step));
        when(steps.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                12L, PromotionApprovalDecision.PENDING)).thenReturn(Optional.of(step));
        when(employees.findById(9L)).thenReturn(Optional.of(approver));
        when(currentUser.getCurrentEmployeeOrSystem()).thenReturn(actor);

        service.reassign(12L, 33L, command);

        assertThat(step.getApprover()).isEqualTo(approver);
        assertThat(step.getApproverGroup()).isNull();
        assertThat(step.getReassignedBy()).isEqualTo(actor);
        assertThat(step.getReassignedAt()).isNotNull();
        assertThat(step.getReassignmentComment()).isEqualTo("Covering the approval queue");
        verify(authorization).requireHrOrAdmin();
        verify(workflow).assignActiveTask(request, 9L);
        verify(activities).record(request, PromotionActivityType.APPROVAL_REASSIGNED, actor,
                "Promotion HR approval reassigned from humanresource to Ben Approver. Covering the approval queue");
        verify(notifications).approvalAssigned(request, step);
    }

    @Test
    void rejectsNonActivePendingStep() {
        PromotionApprovalStep active = new PromotionApprovalStep();
        active.setId(44L);
        active.setDecision(PromotionApprovalDecision.PENDING);
        PromotionReassignmentCommand command = new PromotionReassignmentCommand();
        command.setApproverId(9L);
        when(requests.findById(12L)).thenReturn(Optional.of(request));
        when(steps.findById(33L)).thenReturn(Optional.of(step));
        when(steps.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                12L, PromotionApprovalDecision.PENDING)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.reassign(12L, 33L, command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only the active approval step can be reassigned or delegated.");
    }

    @Test
    void delegatesActivePendingStep() {
        PromotionReassignmentCommand command = new PromotionReassignmentCommand();
        command.setApproverId(9L);
        when(requests.findById(12L)).thenReturn(Optional.of(request));
        when(steps.findById(33L)).thenReturn(Optional.of(step));
        when(steps.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                12L, PromotionApprovalDecision.PENDING)).thenReturn(Optional.of(step));
        when(employees.findById(9L)).thenReturn(Optional.of(approver));
        when(currentUser.getCurrentEmployeeOrSystem()).thenReturn(actor);

        service.delegate(12L, 33L, command);

        assertThat(step.getApprover()).isEqualTo(approver);
        assertThat(step.getDelegatedBy()).isEqualTo(actor);
        assertThat(step.getDelegatedAt()).isNotNull();
        verify(activities).record(request, PromotionActivityType.APPROVAL_DELEGATED, actor,
                "Promotion HR approval delegated from humanresource to Ben Approver.");
    }

    private Employee employee(Long id, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFirstName(name.substring(0, name.indexOf(' ')));
        employee.setLastName(name.substring(name.indexOf(' ') + 1));
        return employee;
    }
}
