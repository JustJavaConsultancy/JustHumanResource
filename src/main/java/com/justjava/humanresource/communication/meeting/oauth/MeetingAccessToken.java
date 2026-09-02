package com.justjava.humanresource.communication.meeting.oauth;

import com.justjava.humanresource.communication.meeting.MeetingProvider;

import java.time.Instant;

public record MeetingAccessToken(
        MeetingProvider provider,
        String tokenType,
        String accessToken,
        Instant expiresAt
) {
    public boolean expiresSoon(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now.plusSeconds(60));
    }

    public String authorizationHeader() {
        String type = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
        return type.substring(0, 1).toUpperCase() + type.substring(1) + " " + accessToken;
    }
}
