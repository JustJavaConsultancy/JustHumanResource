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
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component("processHrLoanDecisionDelegate")
public class ProcessHrLoanDecisionDelegate extends AbstractLoanDecisionDelegate {

    public ProcessHrLoanDecisionDelegate(EmployeeLoanApplicationRepository applications,
                                         EmployeeLoanApprovalStepRepository steps,
                                         LoanApprovalRouteService routeService,
                                         LoanActivityService activityService) {
        super(applications, steps, routeService, activityService);
    }

    @Override protected LoanApprovalStage stage() { return LoanApprovalStage.HR; }
    @Override protected String stageName() { return "HR approval"; }
    @Override protected LoanActivityType approvedActivity() { return LoanActivityType.HR_APPROVED; }
    @Override protected LoanActivityType rejectedActivity() { return LoanActivityType.HR_REJECTED; }

    @Override
    protected void onApprove(DelegateExecution execution, EmployeeLoanApplication app,
                             EmployeeLoanApprovalStep step, Long actorId) {
        app.setHrApprovedAt(LocalDateTime.now());
        app.setHrApprovedByEmployeeId(actorId);
        app.setStatus(LoanApplicationStatus.PENDING_FINANCE_APPROVAL);
    }
}
