package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionApprovalReminderService;
import com.justjava.humanresource.promotion.service.PromotionNotificationService;
import com.justjava.humanresource.promotion.service.PromotionWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionApprovalReminderServiceTest {

    @Mock PromotionApprovalStepRepository steps;
    @Mock PromotionConfigurationRepository configurations;
    @Mock EmployeeRepository employees;
    @Mock PromotionWorkflowService workflow;
    @Mock PromotionActivityService activities;
    @Mock PromotionNotificationService notifications;

    private PromotionApprovalReminderService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        service = new PromotionApprovalReminderService(
                steps,
                configurations,
                employees,
                workflow,
                activities,
                notifications
        );
        now = LocalDateTime.of(2026, 10, 12, 10, 0);
    }

    @Test
    void sendsReminderForDuePendingApproval() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setApprovalReminderAfterHours(4);
        PromotionApprovalStep step = step(now.minusHours(5));
        when(configurations.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        when(steps.findByDecisionOrderByAssignedAtAsc(PromotionApprovalDecision.PENDING))
                .thenReturn(List.of(step));

        PromotionApprovalReminderService.ProcessingResult result = service.processDueApprovals(now);

        assertThat(result.remindersSent()).isEqualTo(1);
        assertThat(result.escalationsApplied()).isZero();
        assertThat(step.getLastReminderSentAt()).isEqualTo(now);
        assertThat(step.getReminderCount()).isEqualTo(1);
        verify(activities).record(step.getPromotionRequest(), PromotionActivityType.APPROVAL_REMINDER_SENT, null,
                "Promotion HR approval reminder sent.");
        verify(notifications).approvalReminder(step.getPromotionRequest(), step);
    }

    @Test
    void escalatesDuePendingApprovalToConfiguredApprover() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setApprovalReminderAfterHours(4);
        configuration.setApprovalEscalationAfterHours(8);
        configuration.setEscalationApproverId(90L);
        Employee escalatedApprover = employee(90L, "Ellen", "Escalation");
        PromotionApprovalStep step = step(now.minusHours(9));
        when(configurations.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        when(steps.findByDecisionOrderByAssignedAtAsc(PromotionApprovalDecision.PENDING))
                .thenReturn(List.of(step));
        when(employees.findById(90L)).thenReturn(Optional.of(escalatedApprover));

        PromotionApprovalReminderService.ProcessingResult result = service.processDueApprovals(now);

        assertThat(result.remindersSent()).isZero();
        assertThat(result.escalationsApplied()).isEqualTo(1);
        assertThat(step.getApprover()).isEqualTo(escalatedApprover);
        assertThat(step.getEscalatedToApprover()).isEqualTo(escalatedApprover);
        assertThat(step.getApproverGroup()).isNull();
        assertThat(step.getEscalatedAt()).isEqualTo(now);
        verify(workflow).assignActiveTask(step.getPromotionRequest(), 90L);
        verify(activities).record(step.getPromotionRequest(), PromotionActivityType.APPROVAL_ESCALATED, null,
                "Promotion HR approval escalated.");
        verify(notifications).approvalEscalated(step.getPromotionRequest(), step);
        verify(notifications, never()).approvalReminder(step.getPromotionRequest(), step);
    }

    @Test
    void escalatesDuePendingApprovalToConfiguredGroup() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setApprovalEscalationAfterHours(8);
        configuration.setEscalationApproverGroup("humanresource");
        PromotionApprovalStep step = step(now.minusHours(10));
        when(configurations.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        when(steps.findByDecisionOrderByAssignedAtAsc(PromotionApprovalDecision.PENDING))
                .thenReturn(List.of(step));

        PromotionApprovalReminderService.ProcessingResult result = service.processDueApprovals(now);

        assertThat(result.escalationsApplied()).isEqualTo(1);
        assertThat(step.getApprover()).isNull();
        assertThat(step.getApproverGroup()).isEqualTo("humanresource");
        assertThat(step.getEscalatedToGroup()).isEqualTo("humanresource");
        verify(workflow).assignActiveTaskToGroup(step.getPromotionRequest(), "humanresource");
    }

    @Test
    void skipsReminderWhenAlreadyReminded() {
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setApprovalReminderAfterHours(4);
        PromotionApprovalStep step = step(now.minusHours(5));
        step.setLastReminderSentAt(now.minusHours(1));
        when(configurations.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        when(steps.findByDecisionOrderByAssignedAtAsc(PromotionApprovalDecision.PENDING))
                .thenReturn(List.of(step));

        PromotionApprovalReminderService.ProcessingResult result = service.processDueApprovals(now);

        assertThat(result.remindersSent()).isZero();
        verify(notifications, never()).approvalReminder(step.getPromotionRequest(), step);
    }

    private PromotionApprovalStep step(LocalDateTime assignedAt) {
        PromotionRequest request = new PromotionRequest();
        request.setId(12L);
        request.setRequestNumber("PR-2026-0012");
        request.setProcessInstanceId("process-12");
        PromotionApprovalStep step = new PromotionApprovalStep();
        step.setId(33L);
        step.setPromotionRequest(request);
        step.setStage(PromotionApprovalStage.HR);
        step.setDecision(PromotionApprovalDecision.PENDING);
        step.setAssignedAt(assignedAt);
        step.setApprover(employee(8L, "Hannah", "HR"));
        return step;
    }

    private Employee employee(Long id, String firstName, String lastName) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        return employee;
    }
}
