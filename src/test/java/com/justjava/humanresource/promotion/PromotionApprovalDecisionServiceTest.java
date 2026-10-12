package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionApprovalDecisionService;
import com.justjava.humanresource.promotion.service.PromotionNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionApprovalDecisionServiceTest {

    @Mock PromotionRequestRepository requests;
    @Mock PromotionApprovalStepRepository steps;
    @Mock EmployeeRepository employees;
    @Mock PromotionActivityService activities;
    @Mock PromotionNotificationService notifications;

    private PromotionApprovalDecisionService service;
    private PromotionRequest request;
    private PromotionApprovalStep step;
    private Employee actor;

    @BeforeEach
    void setUp() {
        service = new PromotionApprovalDecisionService(requests, steps, employees, activities, notifications);
        request = new PromotionRequest();
        request.setId(15L);
        request.setStatus(PromotionStatus.HR_REVIEW);
        step = new PromotionApprovalStep();
        step.setPromotionRequest(request);
        step.setStage(PromotionApprovalStage.HR);
        step.setDecision(PromotionApprovalDecision.PENDING);
        actor = new Employee();
        actor.setId(9L);
        when(requests.findById(15L)).thenReturn(Optional.of(request));
        when(employees.findById(9L)).thenReturn(Optional.of(actor));
    }

    @Test
    void recordsFinalApprovalAndFinalizesRequest() {
        when(steps.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                15L, PromotionApprovalDecision.PENDING))
                .thenReturn(Optional.of(step), Optional.empty(), Optional.empty());

        service.record(15L, PromotionApprovalStage.HR, "APPROVE", "Eligible", 9L, "task-1");
        service.finalizeApproval(15L);

        assertThat(step.getDecision()).isEqualTo(PromotionApprovalDecision.APPROVED);
        assertThat(step.getFlowableTaskId()).isEqualTo("task-1");
        assertThat(request.getStatus()).isEqualTo(PromotionStatus.APPROVED);
        assertThat(request.getApprovedAt()).isNotNull();
        verify(activities).record(request, PromotionActivityType.APPROVED, actor,
                "HR approval completed.");
        verify(notifications).approved(request);
    }

    @Test
    void returnForCorrectionRestoresDraftStateAndAuditsReason() {
        when(steps.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                15L, PromotionApprovalDecision.PENDING)).thenReturn(Optional.of(step));

        service.record(15L, PromotionApprovalStage.HR, "RETURN", "Correct salary", 9L, "task-2");
        service.returnForCorrection(15L, "Correct salary", 9L);

        assertThat(step.getDecision()).isEqualTo(PromotionApprovalDecision.RETURNED);
        assertThat(request.getStatus()).isEqualTo(PromotionStatus.DRAFT);
        assertThat(request.getSubmittedAt()).isNull();
        verify(activities).record(request, PromotionActivityType.RETURNED, actor,
                "Promotion request returned for correction: Correct salary");
        verify(notifications).returned(request);
    }
}
