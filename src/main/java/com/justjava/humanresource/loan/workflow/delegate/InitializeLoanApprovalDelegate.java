package com.justjava.humanresource.loan.workflow.delegate;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.enums.LoanActivityType;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.service.LoanActivityService;
import com.justjava.humanresource.loan.service.LoanApprovalRouteService;
import com.justjava.humanresource.loan.service.LoanEmployeeContextService;
import com.justjava.humanresource.loan.service.LoanNotificationService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * First task of the process. The approval steps already exist (created by LoanApprovalRouteService);
 * this moves the application to its first pending status and sets the variables the BPMN needs.
 */
@Component("initializeLoanApprovalDelegate")
@RequiredArgsConstructor
public class InitializeLoanApprovalDelegate implements JavaDelegate {

    private final EmployeeLoanApplicationRepository applications;
    private final LoanApprovalRouteService routeService;
    private final LoanActivityService activityService;
    private final LoanEmployeeContextService contextService;
    private final LoanNotificationService notifications;

    @Override
    public void execute(DelegateExecution execution) {
        Long id = ((Number) execution.getVariable("loanApplicationId")).longValue();
        EmployeeLoanApplication app = applications.findById(id)
                .orElseThrow(() -> new IllegalStateException("Loan application not found: " + id));
        if (app.getStatus() != LoanApplicationStatus.SUBMITTED) {
            throw new IllegalStateException("Loan application " + app.getApplicationNumber()
                    + " is not in SUBMITTED status.");
        }

        List<EmployeeLoanApprovalStep> steps = routeService.getLatestAttemptSteps(id);
        if (steps.isEmpty()) {
            throw new IllegalStateException("Approval route is empty.");
        }
        EmployeeLoanApprovalStep first = steps.get(0);
        LoanApprovalRouteType routeType = app.getApprovalRouteTypeSnapshot();

        app.setWorkflowInstanceId(execution.getProcessInstanceId());
        execution.setVariable("routeType", routeType.name());
        execution.setVariable("currentLevel", first.getSequenceNo());

        if (routeType == LoanApprovalRouteType.CUSTOM) {
            if (first.getApprovalStage() != LoanApprovalStage.CUSTOM || first.getApproverEmployeeId() == null) {
                throw new IllegalStateException("Custom route is missing its first approver.");
            }
            app.setStatus(LoanApplicationStatus.PENDING_CUSTOM_APPROVAL);
            execution.setVariable("currentApproverId", String.valueOf(first.getApproverEmployeeId()));
            execution.setVariable("hasMoreApprovers", steps.size() > 1);
            String name = contextService.employeeNames(List.of(first.getApproverEmployeeId()))
                    .getOrDefault(first.getApproverEmployeeId(), "approver " + first.getApproverEmployeeId());
            activityService.record(id, LoanActivityType.CUSTOM_APPROVAL_STARTED,
                    "Custom approval started with " + name + " (" + steps.size() + " approver(s) in the path).", null);
        } else {
            if (first.getApprovalStage() != LoanApprovalStage.HR) {
                throw new IllegalStateException("Role-based route must start with the HR step.");
            }
            app.setStatus(LoanApplicationStatus.PENDING_HR_APPROVAL);
            execution.setVariable("hasMoreApprovers", false);
        }
        applications.save(app);

        // Tell the first approver(s) now that the status is PENDING_*; LoanEmailService only sends while the
        // application is still in that status. Scheduled after commit, so nothing is sent if this rolls back.
        if (routeType == LoanApprovalRouteType.CUSTOM) {
            // Never email the applicant as the approver of their own loan.
            if (!Objects.equals(first.getApproverEmployeeId(), app.getEmployee().getId())) {
                notifications.notifyCustomApprovalAssigned(app.getId(), first.getApproverEmployeeId());
            }
        } else {
            notifications.notifyHrApprovalPending(app.getId());
        }
    }
}