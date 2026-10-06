package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanInterestType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculator result. Validation problems are returned in {@code errors} with
 * {@code valid=false} (HTTP 200) so the UI can show them inline while the user types.
 */
@Value
@Builder
public class LoanRepaymentPreviewResponse {
    boolean valid;
    @Builder.Default List<String> errors = new ArrayList<>();
    @Builder.Default List<String> warnings = new ArrayList<>();

    Long loanProductId;
    String loanProductName;
    LoanInterestType interestType;
    BigDecimal interestRate;

    BigDecimal requestedAmount;
    BigDecimal repaymentAmount;
    Integer tenorMonths;
    LocalDate repaymentStartMonth;

    BigDecimal totalInterestAmount;
    BigDecimal totalRepayableAmount;
    /** Installments actually needed (can be fewer than tenorMonths). */
    Integer numberOfInstallments;
    BigDecimal finalInstallmentAmount;
    LocalDate expectedEndMonth;
    /** Smallest monthly repayment that clears the loan within the chosen tenor. */
    BigDecimal minimumRequiredRepaymentAmount;

    @Builder.Default List<LoanRepaymentScheduleLineResponse> lines = new ArrayList<>();
}
