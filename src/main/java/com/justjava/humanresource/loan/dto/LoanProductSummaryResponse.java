package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanInterestType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Lightweight product view: product pickers, employee "available products",
 * and the product block embedded in application responses. Also carries the
 * limits the employee form needs for client-side validation.
 */
@Value
@Builder
public class LoanProductSummaryResponse {
    Long id;
    String code;
    String name;
    String description;
    BigDecimal minimumAmount;
    BigDecimal maximumAmount;
    BigDecimal minimumRepaymentAmount;
    Integer maximumTenorMonths;
    LoanInterestType interestType;
    BigDecimal interestRate;
    LoanApprovalRouteType approvalRouteType;
    String approvalRouteLabel;
    boolean requiresAttachment;
    boolean active;
}
