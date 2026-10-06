package com.justjava.humanresource.loan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Employee payload to create or edit a draft/returned application.
 * The employee is always taken from the authenticated user, never from this body.
 * Product-limit and repayment-sufficiency rules are validated in the service layer.
 */
@Data
public class LoanApplicationCommand {

    @NotNull
    private Long loanProductId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal requestedAmount;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal repaymentAmount;

    @NotNull
    @Min(1)
    private Integer tenorMonths;

    /** First day of the month in which the first deduction is due (yyyy-MM-dd). */
    @NotNull
    private LocalDate repaymentStartMonth;

    @NotBlank
    @Size(max = 2000)
    private String purpose;
}
