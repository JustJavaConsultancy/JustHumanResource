package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
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

    boolean attachmentRequired;
    @Builder.Default List<LoanAttachmentResponse> attachments = new ArrayList<>();

    /** Latest recalculated preview, if available. */
    LoanRepaymentPreviewResponse preview;
}
