package com.justjava.humanresource.loan.workflow.delegate;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.enums.LoanActivityType;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalDecision;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApprovalStepRepository;
import com.justjava.humanresource.loan.service.LoanActivityService;
import com.justjava.humanresource.loan.service.LoanApprovalRouteService;
import com.justjava.humanresource.loan.service.LoanNotificationService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Shared decision handling for the HR, Finance and custom user tasks.
 *
 * Process variables set by the approval service (Step 8) before it completes the task:
 * approvalDecision (APPROVE | REJECT | RETURN), approvalActorId (employee id), approvalComment (optional),
 * flowableTaskId. Loan terms are never touched here.
 */
public abstract class AbstractLoanDecisionDelegate implements JavaDelegate {

    protected final EmployeeLoanApplicationRepository applications;
    protected final EmployeeLoanApprovalStepRepository steps;
    protected final LoanApprovalRouteService routeService;
    protected final LoanActivityService activityService;
    protected final LoanNotificationService notifications;

    protected AbstractLoanDecisionDelegate(EmployeeLoanApplicationRepository applications,
                                           EmployeeLoanApprovalStepRepository steps,
                                           LoanApprovalRouteService routeService,
                                           LoanActivityService activityService,
                                           LoanNotificationService notifications) {
        this.applications = applications;
        this.steps = steps;
        this.routeService = routeService;
        this.activityService = activityService;
        this.notifications = notifications;
    }

    protected abstract LoanApprovalStage stage();

    protected abstract String stageName();

    protected abstract LoanActivityType approvedActivity();

    protected abstract LoanActivityType rejectedActivity();

    /** Status/timestamp changes for an approval at this stage. */
    protected abstract void onApprove(DelegateExecution execution, EmployeeLoanApplication app,
                                      EmployeeLoanApprovalStep step, Long actorId);

    /** Extra check that the actor may decide this step. */
    protected void validateActor(EmployeeLoanApprovalStep step, Long actorId) {
    }

    /**
     * Employee id of the next approver who should be told a task now waits for them, or null for none.
     * Called after an approval has been saved. Delegates are shared singletons, so overrides must work it out
     * from the database and must not keep it in a field.
     */
    protected Long nextAssigneeToNotify(EmployeeLoanApplication app) {
        return null;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long id = ((Number) execution.getVariable("loanApplicationId")).longValue();
        EmployeeLoanApplication app = applications.findById(id)
                .orElseThrow(() -> new IllegalStateException("Loan application not found: " + id));

        String action = String.valueOf(execution.getVariable("approvalDecision"));
        Long actor = ((Number) execution.getVariable("approvalActorId")).longValue();
        String comment = (String) execution.getVariable("approvalComment");
        String taskId = (String) execution.getVariable("flowableTaskId");

        EmployeeLoanApprovalStep step = routeService.getCurrentStep(id)
                .orElseThrow(() -> new IllegalStateException("No pending approval step for " + app.getApplicationNumber()));
        if (step.getApprovalStage() != stage()) {
            throw new IllegalStateException("Pending step is " + step.getApprovalStage() + ", expected " + stage() + ".");
        }
        validateActor(step, actor);

        LoanApprovalDecision decision = switch (action) {
            case "APPROVE" -> LoanApprovalDecision.APPROVE;
            case "REJECT" -> LoanApprovalDecision.REJECT;
            case "RETURN" -> LoanApprovalDecision.RETURN;
            default -> throw new IllegalArgumentException("Unknown approval decision: " + action);
        };
        String cleanComment = comment == null || comment.isBlank() ? null : comment.trim();

        step.setDecision(decision);
        step.setComments(cleanComment);
        step.setDecisionAt(LocalDateTime.now());
        step.setFlowableTaskId(taskId);
        step.setActedByEmployeeId(actor);
        steps.save(step);

        execution.setVariable("hasMoreApprovers", false);
        switch (decision) {
            case APPROVE -> {
                onApprove(execution, app, step, actor);
                activityService.record(id, approvedActivity(), stageName() + " approved."
                        + (cleanComment == null ? "" : " Comment: " + cleanComment), actor);
            }
            case REJECT -> activityService.record(id, rejectedActivity(), stageName() + " rejected."
                    + (cleanComment == null ? "" : " Reason: " + cleanComment), actor);
            case RETURN -> {
                // Recorded as APPLICATION_RETURNED by ReturnLoanForCorrectionDelegate.
            }
        }
        applications.save(app);
        sendDecisionNotifications(id, app, decision, cleanComment);
    }

    /**
     * Employee decision e-mail. Rejections and returns are always sent. An approval is sent only while the
     * application is still moving through approval (HR approved -> Finance next, or a custom approver with
     * more approvers after them). The final approval (FINANCE_APPROVED / CUSTOM_APPROVED) is skipped: the
     * disbursement step sends its own "loan approved" e-mail, so a decision e-mail would be a duplicate.
     * Scheduled after commit, so a rolled-back decision sends nothing.
     */
    private void sendDecisionNotifications(Long id, EmployeeLoanApplication app, LoanApprovalDecision decision,
                                           String comment) {
        boolean finalApproval = decision == LoanApprovalDecision.APPROVE
                && (app.getStatus() == LoanApplicationStatus.FINANCE_APPROVED
                || app.getStatus() == LoanApplicationStatus.CUSTOM_APPROVED);
        if (!finalApproval) {
            notifications.notifyDecision(id, stage(), decision, comment);
        }

        // Role-based route: HR approval hands the loan to Finance, so tell the Finance approvers. Only the HR
        // stage ever reaches PENDING_FINANCE_APPROVAL (custom and Finance decisions never do), so a rejection,
        // a return, a Finance decision or the custom route can't trigger this. The status is already
        // PENDING_FINANCE_APPROVAL here, which LoanEmailService requires before it sends.
        if (decision == LoanApprovalDecision.APPROVE && stage() == LoanApprovalStage.HR
                && app.getStatus() == LoanApplicationStatus.PENDING_FINANCE_APPROVAL) {
            notifications.notifyFinanceApprovalPending(id);
        }

        // Custom route: a non-final approval passes the task to the next custom approver, so tell them.
        // The status must still be PENDING_CUSTOM_APPROVAL (it becomes CUSTOM_APPROVED after the last approver),
        // and nobody is told to approve their own loan.
        if (decision == LoanApprovalDecision.APPROVE && app.getStatus() == LoanApplicationStatus.PENDING_CUSTOM_APPROVAL) {
            Long next = nextAssigneeToNotify(app);
            if (next != null && !Objects.equals(next, app.getEmployee().getId())) {
                notifications.notifyCustomApprovalAssigned(id, next);
            }
        }
    }
}