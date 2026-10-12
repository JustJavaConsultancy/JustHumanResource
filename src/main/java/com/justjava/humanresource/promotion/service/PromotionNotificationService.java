package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.utils.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromotionNotificationService {

    private final PromotionActivityService activityService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PromotionEmailService emailService;

    public void approvalAssigned(PromotionRequest request, PromotionApprovalStep step) {
        if (step == null) {
            return;
        }
        String recipient = step.getApprover() == null
                ? step.getApproverGroup()
                : step.getApprover().getFullName();
        record(request, "Promotion approval assigned to " + recipient + " for " + step.getStage() + ".");
        schedule(() -> emailService.sendApprovalAssigned(request, step));
    }

    public void approvalReminder(PromotionRequest request, PromotionApprovalStep step) {
        if (step == null) {
            return;
        }
        String recipient = step.getApprover() == null
                ? step.getApproverGroup()
                : step.getApprover().getFullName();
        record(request, "Promotion approval reminder queued for " + recipient + " at " + step.getStage() + ".");
        schedule(() -> emailService.sendApprovalReminder(request, step));
    }

    public void approvalEscalated(PromotionRequest request, PromotionApprovalStep step) {
        if (step == null) {
            return;
        }
        String recipient = step.getApprover() == null
                ? step.getApproverGroup()
                : step.getApprover().getFullName();
        record(request, "Promotion approval escalation notification queued for " + recipient + " at " + step.getStage() + ".");
        schedule(() -> emailService.sendApprovalEscalated(request, step));
    }

    public void submitted(PromotionRequest request) {
        record(request, "Promotion request submitted notification queued.");
        schedule(() -> emailService.sendSubmitted(request));
    }

    public void approved(PromotionRequest request) {
        record(request, "Promotion request approved notification queued.");
        schedule(() -> emailService.sendApproved(request));
    }

    public void rejected(PromotionRequest request) {
        record(request, "Promotion request rejected notification queued.");
        schedule(() -> emailService.sendRejected(request));
    }

    public void returned(PromotionRequest request) {
        record(request, "Promotion request returned notification queued.");
        schedule(() -> emailService.sendReturned(request));
    }

    public void cancelled(PromotionRequest request, Employee actor) {
        activityService.record(request, PromotionActivityType.NOTIFICATION_SENT, actor,
                "Promotion cancellation notification queued.");
        schedule(() -> emailService.sendCancelled(request));
    }

    public void implemented(PromotionRequest request, Employee actor) {
        activityService.record(request, PromotionActivityType.NOTIFICATION_SENT, actor,
                "Promotion implementation notification queued.");
        schedule(() -> emailService.sendImplemented(request));
    }

    private void record(PromotionRequest request, String description) {
        activityService.record(request, PromotionActivityType.NOTIFICATION_SENT, null, description);
    }

    private void schedule(Runnable notification) {
        afterCommitExecutor.runAfterCommit(notification);
    }
}
