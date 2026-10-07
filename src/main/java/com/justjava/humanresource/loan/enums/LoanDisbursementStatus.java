package com.justjava.humanresource.loan.enums;

public enum LoanDisbursementStatus {

    /** PAYROLL_PERIOD loan: queued to be paid as an earning line in payroll. */
    SCHEDULED_IN_PAYROLL,

    /** OUTSIDE_PAYROLL loan: final-approved, awaiting Finance to confirm payment. */
    PENDING_EXTERNAL_PAYMENT,

    /** Payment confirmed (external) or posted through payroll. */
    PAID,

    CANCELLED,

    /** Reserved for payroll scheduling/payment failures; unused until a later step needs it. */
    FAILED
}
