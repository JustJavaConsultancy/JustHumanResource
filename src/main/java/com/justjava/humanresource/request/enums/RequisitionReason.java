package com.justjava.humanresource.request.enums;

public enum RequisitionReason {
    NEW_POSITION("New Position"),
    REPLACEMENT("Replacement"),
    EXPANSION("Expansion"),
    TEMPORARY_COVER("Temporary Cover");

    private final String label;

    RequisitionReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
