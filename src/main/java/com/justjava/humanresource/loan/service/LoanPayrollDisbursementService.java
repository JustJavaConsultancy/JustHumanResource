package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.payroll.entity.PayrollRun;

import java.math.BigDecimal;

/**
 * Adds PAYROLL_PERIOD loan disbursements to an employee's payroll run as a
 * non-taxable, non-pensionable, non-gross EARNING line (net pay only).
 */
public interface LoanPayrollDisbursementService {

    /**
     * Rebuilds the LOAN_DISBURSEMENT_&lt;id&gt; earning lines for this (editable) run.
     * Idempotent: existing disbursement lines on the run are removed first.
     *
     * @return total amount added; caller must add it to run.nonGrossEarnings
     */
    BigDecimal applyLoanDisbursements(PayrollRun run);

    /**
     * Called when the run is posted: records payrollRunId / payrollLineItemId
     * on each LoanDisbursement and marks it PAID.
     */
    void markPayrollDisbursementsPosted(PayrollRun run);

    /**
     * Guard for final approval of a PAYROLL_PERIOD loan. Throws if the
     * employee's latest run in the open period is already POSTED.
     */
    void assertCurrentPayrollAllowsDisbursement(Long employeeId);
}
