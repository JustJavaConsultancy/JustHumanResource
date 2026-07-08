package com.justjava.humanresource.request.enums;

public enum ExpensePaymentMethod {
    CASH("Cash"),
    BANK_TRANSFER("Bank Transfer"),
    CARD("Card"),
    MOBILE_MONEY("Mobile Money"),
    PERSONAL_ACCOUNT("Personal Account"),
    OTHER("Other");

    private final String label;

    ExpensePaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
