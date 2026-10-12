package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.service.PromotionEmailNotificationChannel;
import com.justjava.humanresource.promotion.service.PromotionEmailService;
import com.justjava.humanresource.promotion.service.PromotionNotificationChannel;
import com.justjava.humanresource.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionEmailServiceTest {

    @Mock EmailService emailService;
    @Mock EmployeeRepository employeeRepository;
    @Mock PromotionNotificationChannel disabledChannel;

    private PromotionEmailService service;

    @BeforeEach
    void setUp() {
        service = new PromotionEmailService(
                employeeRepository,
                List.of(new PromotionEmailNotificationChannel(emailService))
        );
    }

    @Test
    void sendsSubmittedPromotionEmailToPromotedEmployee() {
        PromotionRequest request = request("ada@example.test");

        service.sendSubmitted(request);

        verify(emailService).sendEmail(
                org.mockito.Mockito.eq("ada@example.test"),
                org.mockito.Mockito.eq("Promotion request submitted"),
                contains("PR-2026-0006"),
                contains("PR-2026-0006")
        );
    }

    @Test
    void skipsEmailWhenRecipientHasNoEmailAddress() {
        PromotionRequest request = request(null);

        service.sendApproved(request);

        verify(emailService, never()).sendEmail(
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString()
        );
    }

    @Test
    void sendsApprovalReminderToAssignedApprover() {
        PromotionRequest request = request("ada@example.test");
        PromotionApprovalStep step = step("manager@example.test");

        service.sendApprovalReminder(request, step);

        verify(emailService).sendEmail(
                org.mockito.Mockito.eq("manager@example.test"),
                org.mockito.Mockito.eq("Promotion approval reminder"),
                contains("PR-2026-0006"),
                contains("LINE_MANAGER")
        );
    }

    @Test
    void sendsEscalationEmailToEscalatedApprover() {
        PromotionRequest request = request("ada@example.test");
        PromotionApprovalStep step = step("manager@example.test");

        service.sendApprovalEscalated(request, step);

        verify(emailService).sendEmail(
                org.mockito.Mockito.eq("manager@example.test"),
                org.mockito.Mockito.eq("Promotion approval escalated"),
                contains("PR-2026-0006"),
                contains("LINE_MANAGER")
        );
    }

    @Test
    void sendsApprovalAssignedEmailToCandidateGroupMembers() {
        PromotionRequest request = request("ada@example.test");
        PromotionApprovalStep step = new PromotionApprovalStep();
        step.setStage(PromotionApprovalStage.HR);
        step.setApproverGroup("humanresource");
        when(employeeRepository.findActiveEmployeesInAnyGroupIgnoreCase(
                List.of("humanresource", "/humanresource")))
                .thenReturn(List.of(employee(20L, "Hannah", "HR", "hannah@example.test")));

        service.sendApprovalAssigned(request, step);

        verify(emailService).sendEmail(
                org.mockito.Mockito.eq("hannah@example.test"),
                org.mockito.Mockito.eq("Promotion approval pending"),
                contains("PR-2026-0006"),
                contains("HR")
        );
    }

    @Test
    void skipsCandidateGroupMembersWithoutEmailAddress() {
        PromotionRequest request = request("ada@example.test");
        PromotionApprovalStep step = new PromotionApprovalStep();
        step.setStage(PromotionApprovalStage.FINANCE);
        step.setApproverGroup("financialofficers");
        when(employeeRepository.findActiveEmployeesInAnyGroupIgnoreCase(
                List.of("financialofficers", "/financialofficers")))
                .thenReturn(List.of(employee(21L, "Finn", "Finance", null)));

        service.sendApprovalAssigned(request, step);

        verify(emailService, never()).sendEmail(
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString()
        );
    }

    @Test
    void skipsDisabledNotificationChannel() {
        when(disabledChannel.enabled()).thenReturn(false);
        service = new PromotionEmailService(employeeRepository, List.of(disabledChannel));

        service.sendSubmitted(request("ada@example.test"));

        verify(disabledChannel, never()).send(
                org.mockito.Mockito.any(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString()
        );
    }

    private PromotionRequest request(String email) {
        PromotionRequest request = new PromotionRequest();
        request.setId(6L);
        request.setRequestNumber("PR-2026-0006");
        request.setEmployee(employee(6L, "Ada", "Lovelace", email));
        return request;
    }

    private PromotionApprovalStep step(String email) {
        Employee approver = new Employee();
        approver.setId(10L);
        approver.setFirstName("Mary");
        approver.setLastName("Manager");
        approver.setEmail(email);
        PromotionApprovalStep step = new PromotionApprovalStep();
        step.setStage(PromotionApprovalStage.LINE_MANAGER);
        step.setApprover(approver);
        return step;
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
