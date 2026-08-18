package com.justjava.humanresource.request.enums;

public enum FileConfidentialityLevel {
    PUBLIC("Public"),
    INTERNAL("Internal"),
    CONFIDENTIAL("Confidential"),
    RESTRICTED("Restricted");

    private final String label;

    FileConfidentialityLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
