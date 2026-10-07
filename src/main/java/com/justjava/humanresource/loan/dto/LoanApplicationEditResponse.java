package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Data needed to populate the draft / returned-for-correction edit form. */
@Value
@Builder
public class LoanApplicationEditResponse {
    Long id;
    String applicationNumber;
    LoanApplicationStatus status;
    /** True only for DRAFT and RETURNED_FOR_CORRECTION owned by the viewer. */
    boolean editable;

    /** Includes the limits the form validates against. */
    LoanProductSummaryResponse loanProduct;

    BigDecimal requestedAmount;
    BigDecimal repaymentAmount;
    Integer tenorMonths;
    LocalDate repaymentStartMonth;
    String purpose;

    /** Approver's comment when status is RETURNED_FOR_CORRECTION. */
    String latestReturnComment;
    String returnedByName;

    LoanDisbursementMethod disbursementMethod;
    String disbursementMethodLabel;
    /** True when the method is OUTSIDE_PAYROLL: submission is blocked until bank details are complete. */
    boolean bankDetailsRequired;
    /** True when not required, otherwise true only if the employee's current bank details are complete. */
    boolean bankDetailsComplete;
    /** The employee's current bank details (read-only in the UI); null unless OUTSIDE_PAYROLL. */
    LoanBankDetailResponse bankDetails;

    boolean attachmentRequired;
    @Builder.Default List<LoanAttachmentResponse> attachments = new ArrayList<>();

    /** Latest recalculated preview, if available. */
    LoanRepaymentPreviewResponse preview;
}
