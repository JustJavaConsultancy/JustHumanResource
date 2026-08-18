package com.justjava.humanresource.request.enums;

public enum FileCategory {
    EMPLOYEE_RECORD("Employee Record"),
    PAYROLL("Payroll"),
    LEGAL("Legal"),
    FINANCE("Finance"),
    POLICY("Policy"),
    COMPLIANCE("Compliance"),
    OPERATIONS("Operations"),
    OTHER("Other");

    private final String label;

    FileCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
