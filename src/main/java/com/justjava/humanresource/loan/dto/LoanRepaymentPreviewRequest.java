package com.justjava.humanresource.loan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Calculator input; same terms as an application, without purpose/attachments. */
@Data
public class LoanRepaymentPreviewRequest {

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
}
