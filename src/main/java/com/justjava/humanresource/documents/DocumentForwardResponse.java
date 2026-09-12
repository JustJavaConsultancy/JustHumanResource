package com.justjava.humanresource.documents;

public record DocumentForwardResponse(
        int documentCount,
        int recipientCount,
        int messageCount
) {
}
