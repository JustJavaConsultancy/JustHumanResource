package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.dto.LoanRepaymentPreviewRequest;
import com.justjava.humanresource.loan.dto.LoanRepaymentPreviewResponse;
import com.justjava.humanresource.loan.entity.LoanProduct;
import com.justjava.humanresource.loan.enums.LoanInterestType;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface LoanRepaymentCalculationService {

    /** Calculator endpoint: loads the product, validates against its limits, returns totals and schedule. */
    LoanRepaymentPreviewResponse preview(LoanRepaymentPreviewRequest request);

    /** Same as above for a product already loaded. Never throws for rule violations; see response.errors. */
    LoanRepaymentPreviewResponse preview(LoanProduct product,
                                         BigDecimal requestedAmount,
                                         BigDecimal repaymentAmount,
                                         Integer tenorMonths,
                                         LocalDate repaymentStartMonth);

    /** Like preview(...) but throws IllegalArgumentException when invalid. Use on draft save/submit. */
    LoanRepaymentPreviewResponse validateOrThrow(LoanProduct product,
                                                 BigDecimal requestedAmount,
                                                 BigDecimal repaymentAmount,
                                                 Integer tenorMonths,
                                                 LocalDate repaymentStartMonth);

    /**
     * Pure schedule math from snapshotted terms (no product limits, no start-month-in-past check).
     * Use this at loan activation so later product edits or a late approval cannot change the schedule.
     */
    LoanRepaymentPreviewResponse calculate(LoanInterestType interestType,
                                           BigDecimal interestRate,
                                           BigDecimal requestedAmount,
                                           BigDecimal repaymentAmount,
                                           Integer tenorMonths,
                                           LocalDate repaymentStartMonth);
}
