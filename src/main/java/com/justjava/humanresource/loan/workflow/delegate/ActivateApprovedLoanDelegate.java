package com.justjava.humanresource.loan.workflow.delegate;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.service.EmployeeLoanAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * End of the approved path (after Finance approval or the last custom approver).
 *
 * Creates the loan account and the locked repayment schedule and sets the application to ACTIVE.
 * Idempotent: a retry after ACTIVE, or after the account already exists, creates nothing new.
 */
@Slf4j
@Component("activateApprovedLoanDelegate")
@RequiredArgsConstructor
public class ActivateApprovedLoanDelegate implements JavaDelegate {

    private final EmployeeLoanApplicationRepository applications;
    private final EmployeeLoanAccountService accountService;

    @Override
    public void execute(DelegateExecution execution) {
        Long id = ((Number) execution.getVariable("loanApplicationId")).longValue();
        EmployeeLoanApplication app = applications.findById(id)
                .orElseThrow(() -> new IllegalStateException("Loan application not found: " + id));
        if (app.getStatus() == LoanApplicationStatus.ACTIVE) {
            return; // idempotent on retry
        }
        if (app.getStatus() != LoanApplicationStatus.FINANCE_APPROVED
                && app.getStatus() != LoanApplicationStatus.CUSTOM_APPROVED) {
            throw new IllegalStateException("Loan " + app.getApplicationNumber()
                    + " cannot be activated from status " + app.getStatus() + ".");
        }
        accountService.activate(id);
        log.info("Loan {} activated: account and repayment schedule created.", app.getApplicationNumber());
    }
}