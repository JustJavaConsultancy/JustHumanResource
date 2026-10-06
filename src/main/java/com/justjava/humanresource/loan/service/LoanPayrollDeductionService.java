package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.payroll.entity.PayrollRun;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bridges the locked loan repayment schedules and payroll.
 *
 * Two phases keep payroll retries and recalculations idempotent:
 * 1. {@link #applyLoanDeductions} (during calculation) only writes payroll line items. It never touches
 *    loan balances, so it can run any number of times for the same run.
 * 2. {@link #recordPostedDeductions} (when the run is posted) records the repayment transactions and
 *    updates schedule rows and balances, once.
 */
public interface LoanPayrollDeductionService {

    /** Line item code prefix. The full code is LOAN_REPAYMENT_{loanAccountId}. */
    String LINE_CODE_PREFIX = "LOAN_REPAYMENT_";

    /**
     * Replaces this run's loan deduction line items with the installments due in the payroll month.
     * Existing loan lines of the run are removed first, so repeated calls give the same result.
     * An installment is capped at the net pay still available, so net pay never goes negative.
     *
     * @param netBeforeLoan gross + non-gross earnings - statutory deductions - other deductions
     * @return total of the loan deduction lines added (zero when nothing is due)
     */
    BigDecimal applyLoanDeductions(PayrollRun run, BigDecimal netBeforeLoan);

    /**
     * Called as the run is posted. For each loan line item records the repayment transaction, updates the
     * schedule row and account balance, completes fully repaid loans, and flags the employee's earlier
     * installments that were never deducted as MISSED. Amounts already deducted by an earlier run of the same
     * month (amendments) are not applied twice.
     */
    void recordPostedDeductions(PayrollRun run);

    /**
     * Flags every installment due in the given month that is still unpaid or part-paid as MISSED.
     * Visibility only: nothing is rolled forward or re-deducted. Intended for period close.
     *
     * @return number of rows flagged
     */
    int flagMissedDeductions(LocalDate month);
}
