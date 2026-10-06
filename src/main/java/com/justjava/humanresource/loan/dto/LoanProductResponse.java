package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanInterestType;
import com.justjava.humanresource.loan.enums.LoanRepaymentFrequency;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Full loan product view for HR setup screens. */
@Value
@Builder
public class LoanProductResponse {
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
    LoanRepaymentFrequency repaymentFrequency;
    LoanApprovalRouteType approvalRouteType;
    /** Human label, e.g. "Role-based HR then Finance" or "Custom approval path". */
    String approvalRouteLabel;
    Long customApprovalPathId;
    String customApprovalPathName;
    boolean requiresAttachment;
    boolean active;
    boolean used;

    /** Number of applications (any status) that reference this product. */
    long applicationCount;
    /** Number of currently ACTIVE loan accounts created from this product. */
    long activeLoanCount;

    /** True when financial terms and approval route may still be edited (product unused). */
    boolean financialTermsEditable;
    /** True when the product may be deleted (product unused). */
    boolean deletable;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
