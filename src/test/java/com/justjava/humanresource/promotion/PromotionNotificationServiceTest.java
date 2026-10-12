package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionEmailService;
import com.justjava.humanresource.promotion.service.PromotionNotificationService;
import com.justjava.humanresource.utils.AfterCommitExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PromotionNotificationServiceTest {

    @Mock PromotionActivityService activityService;
    @Mock AfterCommitExecutor afterCommitExecutor;
    @Mock PromotionEmailService emailService;

    private PromotionNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PromotionNotificationService(activityService, afterCommitExecutor, emailService);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(afterCommitExecutor).runAfterCommit(any(Runnable.class));
    }

    @Test
    void submittedRecordsActivityAndSchedulesEmailAfterCommit() {
        PromotionRequest request = request();

        service.submitted(request);

        verify(activityService).record(request, PromotionActivityType.NOTIFICATION_SENT, null,
                "Promotion request submitted notification queued.");
        verify(afterCommitExecutor).runAfterCommit(any(Runnable.class));
        verify(emailService).sendSubmitted(request);
    }

    @Test
    void approvalAssignedRecordsRecipientAndSchedulesApproverEmailAfterCommit() {
        PromotionRequest request = request();
        PromotionApprovalStep step = new PromotionApprovalStep();
        step.setStage(PromotionApprovalStage.HR);
        step.setApprover(employee(8L, "Hannah", "HR", "hannah@example.test"));

        service.approvalAssigned(request, step);

        verify(activityService).record(request, PromotionActivityType.NOTIFICATION_SENT, null,
                "Promotion approval assigned to Hannah HR for HR.");
        verify(afterCommitExecutor).runAfterCommit(any(Runnable.class));
        verify(emailService).sendApprovalAssigned(request, step);
    }

    @Test
    void approvalReminderRecordsActivityAndSchedulesReminderEmailAfterCommit() {
        PromotionRequest request = request();
        PromotionApprovalStep step = new PromotionApprovalStep();
        step.setStage(PromotionApprovalStage.FINANCE);
        step.setApprover(employee(9L, "Finn", "Finance", "finn@example.test"));

        service.approvalReminder(request, step);

        verify(activityService).record(request, PromotionActivityType.NOTIFICATION_SENT, null,
                "Promotion approval reminder queued for Finn Finance at FINANCE.");
        verify(emailService).sendApprovalReminder(request, step);
    }

    @Test
    void approvalEscalatedRecordsActivityAndSchedulesEscalationEmailAfterCommit() {
        PromotionRequest request = request();
        PromotionApprovalStep step = new PromotionApprovalStep();
        step.setStage(PromotionApprovalStage.EXECUTIVE);
        step.setApprover(employee(10L, "Eve", "Executive", "eve@example.test"));

        service.approvalEscalated(request, step);

        verify(activityService).record(request, PromotionActivityType.NOTIFICATION_SENT, null,
                "Promotion approval escalation notification queued for Eve Executive at EXECUTIVE.");
        verify(emailService).sendApprovalEscalated(request, step);
    }

    private PromotionRequest request() {
        PromotionRequest request = new PromotionRequest();
        request.setId(5L);
        request.setRequestNumber("PR-2026-0005");
        request.setEmployee(employee(2L, "Ada", "Lovelace", "ada@example.test"));
        return request;
    }

    private Employee employee(Long id, String firstName, String lastName, String email) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(email);
        return employee;
    }
}
