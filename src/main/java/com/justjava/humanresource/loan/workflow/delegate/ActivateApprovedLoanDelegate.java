package com.justjava.humanresource.loan.workflow.delegate;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.service.LoanDisbursementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * End of the approved path (after Finance approval or the last custom approver).
 *
 * The bean/task name is kept for BPMN compatibility, but this task now FINALIZES the approved loan and
 * does not always activate it. {@link LoanDisbursementService#handleFinalApproval} decides:
 * <ul>
 *   <li>PAYROLL_PERIOD: the loan is activated now (account + locked schedule, status ACTIVE).</li>
 *   <li>OUTSIDE_PAYROLL: status becomes PENDING_DISBURSEMENT; no account or schedule exists until Finance
 *       confirms payment.</li>
 * </ul>
 * Idempotent: a retry after the loan is ACTIVE or PENDING_DISBURSEMENT does nothing.
 */
@Slf4j
@Component("activateApprovedLoanDelegate")
@RequiredArgsConstructor
public class ActivateApprovedLoanDelegate implements JavaDelegate {

    private final EmployeeLoanApplicationRepository applications;
    private final LoanDisbursementService disbursementService;

    @Override
    public void execute(DelegateExecution execution) {
        Long id = ((Number) execution.getVariable("loanApplicationId")).longValue();
        EmployeeLoanApplication app = applications.findById(id)
                .orElseThrow(() -> new IllegalStateException("Loan application not found: " + id));

        LoanApplicationStatus status = app.getStatus();
        if (status == LoanApplicationStatus.ACTIVE
                || status == LoanApplicationStatus.PENDING_DISBURSEMENT
                || status == LoanApplicationStatus.COMPLETED) {
            return; // idempotent on retry
        }
        if (status != LoanApplicationStatus.FINANCE_APPROVED
                && status != LoanApplicationStatus.CUSTOM_APPROVED) {
            throw new IllegalStateException("Loan " + app.getApplicationNumber()
                    + " cannot be finalized from status " + status + ".");
        }

        Long finalApproverId = app.getFinalApprovedByEmployeeId() != null
                ? app.getFinalApprovedByEmployeeId()
                : app.getFinanceApprovedByEmployeeId();
        disbursementService.handleFinalApproval(id, finalApproverId);
        log.info("Loan {} final approval handled; status is now {}.", app.getApplicationNumber(),
                applications.findById(id).map(EmployeeLoanApplication::getStatus).orElse(null));
    }
}