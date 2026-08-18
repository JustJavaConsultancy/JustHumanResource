package com.justjava.humanresource.request.enums;

public enum ExpenseCategory {
    TRANSPORT("Transport"),
    ACCOMMODATION("Accommodation"),
    MEALS("Meals"),
    OFFICE_SUPPLIES("Office Supplies"),
    COMMUNICATION("Communication"),
    TRAINING("Training"),
    CLIENT_MEETING("Client Meeting"),
    MEDICAL("Medical"),
    OTHER("Other");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
