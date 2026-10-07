package com.justjava.humanresource.loan.enums;

/**
 * How an approved loan principal is paid out to the employee.
 * Configured on the loan product and snapshotted on the application at submission.
 */
public enum LoanDisbursementMethod {

    /** Principal is added to the employee's payroll as a fixed non-gross earning. */
    PAYROLL_PERIOD("Pay inside payroll period"),

    /** Principal is paid outside payroll; Finance must confirm payment before activation. */
    OUTSIDE_PAYROLL("Pay outside payroll system");

    private final String label;

    LoanDisbursementMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
