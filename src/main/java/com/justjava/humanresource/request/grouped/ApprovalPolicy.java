package com.justjava.humanresource.request.grouped;

public enum ApprovalPolicy {
    ALL, ANY_ONE;

    public String description() {
        return this == ALL
                ? "Everyone must approve; one rejection rejects the request."
                : "The first decision resolves this level; other pending tasks close.";
    }
}
