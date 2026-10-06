package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.dto.EmployeeLoanDashboardResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationCommand;
import com.justjava.humanresource.loan.dto.LoanApplicationDetailResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationEditResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationSummaryResponse;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;

import java.util.List;

/**
 * Employee-side application lifecycle plus the role-specific detail views.
 * Submit starts the approval workflow and cancel stops it, both through LoanApprovalRouteService.
 */
public interface EmployeeLoanApplicationService {

    /** Creates a DRAFT for the logged-in employee and marks the product as used. */
    LoanApplicationResponse createDraft(LoanApplicationCommand command);

    /** DRAFT or RETURNED_FOR_CORRECTION, owner only. */
    LoanApplicationResponse updateApplication(Long id, LoanApplicationCommand command);

    /** DRAFT only, owner only. Hard delete including attachments and activity. */
    void deleteDraft(Long id);

    /** DRAFT or RETURNED_FOR_CORRECTION to SUBMITTED, with snapshots and totals fixed at this point. */
    LoanApplicationResponse submit(Long id);

    /** Owner only; not allowed once any approver has approved the current submission. */
    LoanApplicationResponse cancel(Long id);

    LoanApplicationEditResponse getForEdit(Long id);

    LoanApplicationDetailResponse getEmployeeDetail(Long id);

    LoanApplicationDetailResponse getHrDetail(Long id);

    LoanApplicationDetailResponse getFinanceDetail(Long id);

    LoanApplicationDetailResponse getCustomApproverDetail(Long id);

    /** The logged-in employee's applications, newest first; status filter optional. */
    List<LoanApplicationSummaryResponse> listMine(LoanApplicationStatus status);

    EmployeeLoanDashboardResponse getMyDashboard();
}