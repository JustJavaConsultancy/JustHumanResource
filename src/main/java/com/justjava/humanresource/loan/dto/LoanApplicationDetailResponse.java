package com.justjava.humanresource.loan.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Single payload behind the employee, HR, Finance, and custom-approver detail pages.
 * Sections the viewer is not entitled to see are left null/empty by the service
 * (e.g. approvalContext is null for the employee's own view).
 */
@Value
@Builder
public class LoanApplicationDetailResponse {
    LoanApplicationResponse application;
    LoanProductSummaryResponse loanProduct;

    /** Employee context + exposure for HR/Finance/custom approvers. */
    LoanApprovalContextResponse approvalContext;

    /** Route type, custom path name snapshot, and step timeline. */
    LoanApprovalRouteResponse approvalRoute;

    /** Preview before final approval, locked schedule afterwards. */
    boolean scheduleLocked;
    @Builder.Default List<LoanRepaymentScheduleLineResponse> repaymentSchedule = new ArrayList<>();
    @Builder.Default List<LoanRepaymentTransactionResponse> repaymentHistory = new ArrayList<>();
    @Builder.Default List<LoanMissedDeductionResponse> missedDeductions = new ArrayList<>();

    // Loan account (null until activated)
    Long loanAccountId;
    String loanAccountStatus;
    BigDecimal totalPaidAmount;
    BigDecimal outstandingBalance;

    @Builder.Default List<LoanAttachmentResponse> attachments = new ArrayList<>();
    @Builder.Default List<LoanActivityResponse> activities = new ArrayList<>();

    // Viewer permissions
    /** EMPLOYEE, HR, FINANCE, CUSTOM_APPROVER. */
    String viewerRole;
    boolean canEdit;
    boolean canDelete;
    boolean canSubmit;
    boolean canCancel;
    boolean canUploadAttachment;
    /** True when the viewer has an actionable approval task on this application. */
    boolean canAct;
    /** Flowable task id the viewer should act on; null when canAct is false. */
    String currentTaskId;
}
