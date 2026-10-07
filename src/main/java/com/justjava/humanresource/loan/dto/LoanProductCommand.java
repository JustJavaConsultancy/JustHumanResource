package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanInterestType;
import com.justjava.humanresource.loan.enums.LoanRepaymentFrequency;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * HR payload for creating or updating a loan product.
 *
 * Cross-field rules (maximumAmount >= minimumAmount, interestRate required for
 * INTEREST_BEARING, customApprovalPathId required and valid for CUSTOM route, and
 * the "used product" edit restrictions) are enforced in LoanProductServiceImpl.
 * The disbursement method is required (no default) and is part of the locked set once a product is used.
 * Active/inactive state is changed only through the deactivate/reactivate endpoints.
 */
@Data
public class LoanProductCommand {

    @NotBlank
    @Size(max = 50)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal minimumAmount;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal maximumAmount;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal minimumRepaymentAmount;

    @NotNull
    @Min(1)
    private Integer maximumTenorMonths;

    @NotNull
    private LoanInterestType interestType = LoanInterestType.INTEREST_FREE;

    /** Annual percentage rate (0-100). Required when interestType is INTEREST_BEARING. */
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal interestRate;

    @NotNull
    private LoanRepaymentFrequency repaymentFrequency = LoanRepaymentFrequency.MONTHLY;

    @NotNull
    private LoanApprovalRouteType approvalRouteType = LoanApprovalRouteType.ROLE_BASED;

    /** Required when approvalRouteType is CUSTOM; must be null/ignored for ROLE_BASED. */
    private Long customApprovalPathId;

    /** Required. How the approved principal is paid out; locked once the product is used. */
    @NotNull
    private LoanDisbursementMethod disbursementMethod;

    private boolean requiresAttachment;
}
