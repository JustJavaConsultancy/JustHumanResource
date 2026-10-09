package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.entity.EmployeeLoanAccount;
import com.justjava.humanresource.loan.entity.LoanRepaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Loan account lifecycle: activation after final approval, repayment application and completion.
 * Callers (workflow delegates, payroll) are responsible for access checks.
 */
public interface EmployeeLoanAccountService {

    /**
     * Creates the loan account and the locked repayment schedule for a fully approved application
     * (FINANCE_APPROVED or CUSTOM_APPROVED) and sets the application to ACTIVE.
     * Idempotent: if the account already exists it is returned and nothing is duplicated.
     * Schedule maths uses the application's snapshotted terms, never the live product.
     */
    EmployeeLoanAccount activate(Long loanApplicationId);

    /**
     * Same as {@link #activate(Long)} but the schedule starts at {@code effectiveRepaymentStartMonth}
     * (normalised to the 1st; cannot be before the employee-selected month). Also accepts applications in
     * PENDING_DISBURSEMENT, so only the disbursement service should call it. The application keeps the
     * employee-selected month; the effective month is stored on the application and the account.
     * Idempotent: an existing account is never changed or duplicated.
     */
    EmployeeLoanAccount activate(Long loanApplicationId, LocalDate effectiveRepaymentStartMonth);

    /** ACTIVE accounts of an employee, newest first. */
    List<EmployeeLoanAccount> getActiveLoansByEmployee(Long employeeId);

    /** Sum of outstanding balances across the employee's ACTIVE accounts. */
    BigDecimal getOutstandingBalance(Long employeeId);

    /**
     * Applies a successful payroll deduction to one schedule row: records the transaction, updates the row
     * (PARTIALLY_PAID / PAID), reduces the account balance and completes the loan at zero balance.
     * Idempotent per (schedule row, payroll run): a repeat call returns the existing transaction.
     */
    LoanRepaymentTransaction applyRepayment(Long repaymentScheduleId, BigDecimal amount,
                                            Long payrollRunId, Long payrollLineItemId);

    /**
     * Flags a due row as MISSED (visibility only: no roll-forward, no schedule change).
     * Idempotent; ignored for rows that are already PAID/MISSED.
     *
     * @return true when this call changed the row to MISSED; false when nothing changed (row already PAID or
     *         MISSED, or its loan account is not ACTIVE). Callers use it to notify only newly missed rows.
     */
    boolean markMissed(Long repaymentScheduleId);
}