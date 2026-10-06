package com.justjava.humanresource.loan.workflow.delegate;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.enums.LoanActivityType;
import com.justjava.humanresource.loan.enums.LoanApprovalDecision;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApprovalStepRepository;
import com.justjava.humanresource.loan.service.LoanActivityService;
import com.justjava.humanresource.loan.service.LoanApprovalRouteService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

import java.time.LocalDateTime;

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

    protected AbstractLoanDecisionDelegate(EmployeeLoanApplicationRepository applications,
                                           EmployeeLoanApprovalStepRepository steps,
                                           LoanApprovalRouteService routeService,
                                           LoanActivityService activityService) {
        this.applications = applications;
        this.steps = steps;
        this.routeService = routeService;
        this.activityService = activityService;
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
    }
}
