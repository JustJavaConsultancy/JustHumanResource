package com.justjava.humanresource.request.enums;

public enum FileAccessType {
    VIEW("View Only"),
    DOWNLOAD("Download"),
    EDIT("Edit"),
    SHARE("Share"),
    FULL_ACCESS("Full Access");

    private final String label;

    FileAccessType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
