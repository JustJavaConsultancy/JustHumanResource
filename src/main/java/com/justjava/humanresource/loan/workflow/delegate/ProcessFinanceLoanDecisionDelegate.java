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

@Component("processFinanceLoanDecisionDelegate")
public class ProcessFinanceLoanDecisionDelegate extends AbstractLoanDecisionDelegate {

    public ProcessFinanceLoanDecisionDelegate(EmployeeLoanApplicationRepository applications,
                                              EmployeeLoanApprovalStepRepository steps,
                                              LoanApprovalRouteService routeService,
                                              LoanActivityService activityService,
                                              LoanNotificationService notifications) {
        super(applications, steps, routeService, activityService, notifications);
    }

    @Override protected LoanApprovalStage stage() { return LoanApprovalStage.FINANCE; }
    @Override protected String stageName() { return "Finance approval"; }
    @Override protected LoanActivityType approvedActivity() { return LoanActivityType.FINANCE_APPROVED; }
    @Override protected LoanActivityType rejectedActivity() { return LoanActivityType.FINANCE_REJECTED; }

    @Override
    protected void onApprove(DelegateExecution execution, EmployeeLoanApplication app,
                             EmployeeLoanApprovalStep step, Long actorId) {
        app.setFinanceApprovedAt(LocalDateTime.now());
        app.setFinanceApprovedByEmployeeId(actorId);
        // Finance is the last approver on the role-based route.
        app.setFinalApprovedAt(LocalDateTime.now());
        app.setFinalApprovedByEmployeeId(actorId);
        app.setStatus(LoanApplicationStatus.FINANCE_APPROVED);
    }
}