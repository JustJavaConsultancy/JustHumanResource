package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionApprovalReminderService {

    private final PromotionApprovalStepRepository stepRepository;
    private final PromotionConfigurationRepository configurationRepository;
    private final EmployeeRepository employeeRepository;
    private final PromotionWorkflowService workflowService;
    private final PromotionActivityService activityService;
    private final PromotionNotificationService notificationService;

    @Scheduled(fixedDelayString = "${promotion.approval-reminder-scan-ms:3600000}")
    @Transactional
    public void processDueApprovals() {
        processDueApprovals(LocalDateTime.now());
    }

    @Transactional
    public ProcessingResult processDueApprovals(LocalDateTime now) {
        PromotionConfiguration configuration = activeConfiguration();
        int reminders = 0;
        int escalations = 0;
        List<PromotionApprovalStep> pendingSteps =
                stepRepository.findByDecisionOrderByAssignedAtAsc(PromotionApprovalDecision.PENDING);
        for (PromotionApprovalStep step : pendingSteps) {
            if (step.getPromotionRequest() == null || step.getAssignedAt() == null) {
                continue;
            }
            if (isEscalationDue(step, configuration, now) && hasEscalationTarget(configuration)) {
                escalate(step, configuration, now);
                escalations++;
            } else if (isReminderDue(step, configuration, now)) {
                remind(step, now);
                reminders++;
            }
        }
        return new ProcessingResult(reminders, escalations);
    }

    private PromotionConfiguration activeConfiguration() {
        return configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc()
                .orElseGet(PromotionConfiguration::new);
    }

    private boolean isReminderDue(PromotionApprovalStep step, PromotionConfiguration configuration, LocalDateTime now) {
        Integer hours = configuration.getApprovalReminderAfterHours();
        return hours != null
                && hours > 0
                && step.getLastReminderSentAt() == null
                && !step.getAssignedAt().isAfter(now.minusHours(hours));
    }

    private boolean isEscalationDue(PromotionApprovalStep step, PromotionConfiguration configuration, LocalDateTime now) {
        Integer hours = configuration.getApprovalEscalationAfterHours();
        return hours != null
                && hours > 0
                && step.getEscalatedAt() == null
                && !step.getAssignedAt().isAfter(now.minusHours(hours));
    }

    private boolean hasEscalationTarget(PromotionConfiguration configuration) {
        return configuration.getEscalationApproverId() != null
                || (configuration.getEscalationApproverGroup() != null
                && !configuration.getEscalationApproverGroup().isBlank());
    }

    private void remind(PromotionApprovalStep step, LocalDateTime now) {
        PromotionRequest request = step.getPromotionRequest();
        step.setLastReminderSentAt(now);
        step.setReminderCount(step.getReminderCount() == null ? 1 : step.getReminderCount() + 1);
        stepRepository.save(step);
        activityService.record(request, PromotionActivityType.APPROVAL_REMINDER_SENT, null,
                "Promotion " + step.getStage() + " approval reminder sent.");
        notificationService.approvalReminder(request, step);
    }

    private void escalate(PromotionApprovalStep step, PromotionConfiguration configuration, LocalDateTime now) {
        PromotionRequest request = step.getPromotionRequest();
        if (step.getOriginalApprover() == null) {
            step.setOriginalApprover(step.getApprover());
        }
        step.setEscalatedAt(now);
        if (configuration.getEscalationApproverId() != null) {
            Employee escalatedApprover = employeeRepository.findById(configuration.getEscalationApproverId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Escalation approver not found: " + configuration.getEscalationApproverId()));
            step.setApprover(escalatedApprover);
            step.setApproverGroup(null);
            step.setEscalatedToApprover(escalatedApprover);
            step.setEscalatedToGroup(null);
            step.setAssignedAt(now);
            step.setEscalationComment("Escalated to " + escalatedApprover.getFullName() + ".");
            stepRepository.save(step);
            workflowService.assignActiveTask(request, escalatedApprover.getId());
        } else {
            String group = configuration.getEscalationApproverGroup().trim();
            step.setApprover(null);
            step.setApproverGroup(group);
            step.setEscalatedToApprover(null);
            step.setEscalatedToGroup(group);
            step.setAssignedAt(now);
            step.setEscalationComment("Escalated to group " + group + ".");
            stepRepository.save(step);
            workflowService.assignActiveTaskToGroup(request, group);
        }
        activityService.record(request, PromotionActivityType.APPROVAL_ESCALATED, null,
                "Promotion " + step.getStage() + " approval escalated.");
        notificationService.approvalEscalated(request, step);
    }

    public record ProcessingResult(int remindersSent, int escalationsApplied) {
    }
}
