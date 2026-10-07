package com.justjava.humanresource.loan.enums;

public enum LoanApplicationStatus {
    DRAFT,
    SUBMITTED,
    PENDING_HR_APPROVAL,
    PENDING_CUSTOM_APPROVAL,
    RETURNED_FOR_CORRECTION,
    HR_APPROVED,
    PENDING_FINANCE_APPROVAL,
    FINANCE_APPROVED,
    CUSTOM_APPROVED,
    /** Final-approved OUTSIDE_PAYROLL loan awaiting Finance payment confirmation. No loan account yet. */
    PENDING_DISBURSEMENT,
    ACTIVE,
    REJECTED,
    CANCELLED,
    COMPLETED,
    CLOSED
}