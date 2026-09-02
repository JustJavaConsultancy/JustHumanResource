package com.justjava.humanresource.documents;

import com.justjava.humanresource.communication.dto.ChatMessageResponse;

import java.util.List;

public record DocumentForwardResult(
        DocumentForwardResponse response,
        List<ChatMessageResponse> messages
) {
}
