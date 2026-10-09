package com.justjava.humanresource.loan.workflow.delegate;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.enums.LoanActivityType;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApprovalStepRepository;
import com.justjava.humanresource.loan.service.LoanActivityService;
import com.justjava.humanresource.loan.service.LoanApprovalRouteService;
import com.justjava.humanresource.loan.service.LoanNotificationService;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component("processCustomLoanDecisionDelegate")
public class ProcessCustomLoanDecisionDelegate extends AbstractLoanDecisionDelegate {

    public ProcessCustomLoanDecisionDelegate(EmployeeLoanApplicationRepository applications,
                                             EmployeeLoanApprovalStepRepository steps,
                                             LoanApprovalRouteService routeService,
                                             LoanActivityService activityService,
                                             LoanNotificationService notifications) {
        super(applications, steps, routeService, activityService, notifications);
    }

    @Override protected LoanApprovalStage stage() { return LoanApprovalStage.CUSTOM; }
    @Override protected String stageName() { return "Custom approval"; }
    @Override protected LoanActivityType approvedActivity() { return LoanActivityType.CUSTOM_APPROVED; }
    @Override protected LoanActivityType rejectedActivity() { return LoanActivityType.CUSTOM_REJECTED; }

    @Override
    protected void validateActor(EmployeeLoanApprovalStep step, Long actorId) {
        if (!actorId.equals(step.getApproverEmployeeId())) {
            throw new IllegalStateException("Approval actor does not match the configured approver.");
        }
    }

    /** The decision is already saved, so the current step is the next custom approver (none after the last). */
    @Override
    protected Long nextAssigneeToNotify(EmployeeLoanApplication app) {
        return routeService.getCurrentStep(app.getId())
                .filter(next -> next.getApprovalStage() == LoanApprovalStage.CUSTOM)
                .map(EmployeeLoanApprovalStep::getApproverEmployeeId)
                .orElse(null);
    }

    @Override
    protected void onApprove(DelegateExecution execution, EmployeeLoanApplication app,
                             EmployeeLoanApprovalStep step, Long actorId) {
        // The decision on this step is already saved, so the current step is now the next approver.
        Optional<EmployeeLoanApprovalStep> next = routeService.getCurrentStep(app.getId());
        if (next.isPresent() && next.get().getApprovalStage() == LoanApprovalStage.CUSTOM) {
            execution.setVariable("hasMoreApprovers", true);
            execution.setVariable("currentLevel", next.get().getSequenceNo());
            execution.setVariable("currentApproverId", String.valueOf(next.get().getApproverEmployeeId()));
        } else {
            LocalDateTime now = LocalDateTime.now();
            app.setCustomApprovalCompletedAt(now);
            // The final custom approver is the last approver on the custom route.
            app.setFinalApprovedAt(now);
            app.setFinalApprovedByEmployeeId(actorId);
            app.setStatus(LoanApplicationStatus.CUSTOM_APPROVED);
        }
    }
}