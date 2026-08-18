package com.justjava.humanresource.request.enums;

public enum StaffEmploymentType {
    FULL_TIME("Full Time"),
    PART_TIME("Part Time"),
    CONTRACT("Contract"),
    TEMPORARY("Temporary"),
    INTERN("Intern"),
    CONSULTANT("Consultant");

    private final String label;

    StaffEmploymentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
