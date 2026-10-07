package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.dto.LoanConfirmDisbursementCommand;
import com.justjava.humanresource.loan.dto.LoanDisbursementFilter;
import com.justjava.humanresource.loan.dto.LoanDisbursementResponse;

import java.util.List;

/**
 * Disbursement layer of the loan lifecycle. Final approval no longer always means ACTIVE:
 * <ul>
 *   <li>PAYROLL_PERIOD: disbursement row is created and the loan is activated immediately.</li>
 *   <li>OUTSIDE_PAYROLL: disbursement row waits in PENDING_EXTERNAL_PAYMENT and the application sits in
 *       PENDING_DISBURSEMENT (no account, no schedule) until Finance confirms payment.</li>
 * </ul>
 */
public interface LoanDisbursementService {

    /**
     * Called after the last approver approves (application is FINANCE_APPROVED or CUSTOM_APPROVED).
     * Idempotent: a second call never duplicates the disbursement, the account or the schedule.
     * No access check: invoked from the workflow, not from a user request.
     */
    void handleFinalApproval(Long loanApplicationId, Long finalApproverEmployeeId);

    /**
     * Finance confirms an outside-payroll loan was paid. Requires Finance or Admin. Activates the loan with
     * effectiveRepaymentStartMonth = max(selected month, month after the paid month). Cannot be run twice.
     */
    LoanDisbursementResponse confirmExternalPaid(Long disbursementId, LoanConfirmDisbursementCommand command);

    /** Server-side filtered list. Pending rows oldest first; everything else newest paid/approved first. */
    List<LoanDisbursementResponse> listForFinance(LoanDisbursementFilter filter);

    LoanDisbursementResponse getById(Long disbursementId);

    /**
     * CSV (UTF-8 with BOM, CRLF) of every row matching the filter. Same filter as {@link #listForFinance},
     * never limited to one row or one page.
     */
    byte[] exportCsvForFinance(LoanDisbursementFilter filter);
}
