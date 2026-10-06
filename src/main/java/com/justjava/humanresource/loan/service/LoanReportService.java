package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.dto.FinanceLoanDashboardResponse;
import com.justjava.humanresource.loan.dto.HrLoanDashboardResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationSummaryResponse;
import com.justjava.humanresource.loan.dto.LoanMissedDeductionResponse;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;

import java.util.List;

/**
 * Read-only staff views over all loans: HR/Finance application lists, dashboards and missed deductions.
 * Other employees' drafts are never included.
 */
public interface LoanReportService {

    /** HR/admin. All submitted applications (never drafts), newest first; status filter optional. */
    List<LoanApplicationSummaryResponse> listApplicationsForHr(LoanApplicationStatus status);

    /** Finance/admin. Finance approval queue plus loans that affect payroll (approved, active, completed, closed). */
    List<LoanApplicationSummaryResponse> listApplicationsForFinance(LoanApplicationStatus status);

    /** HR/admin. */
    HrLoanDashboardResponse getHrDashboard();

    /** Finance/admin. */
    FinanceLoanDashboardResponse getFinanceDashboard();

    /** HR, Finance or admin. Installments flagged MISSED, newest due month first. */
    List<LoanMissedDeductionResponse> listMissedDeductions();
}
