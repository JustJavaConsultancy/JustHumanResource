package com.justjava.humanresource.communication.meeting.oauth;

import java.time.Instant;
import java.util.Map;

abstract class AbstractMeetingTokenClient {

    protected boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    protected MeetingAccessToken tokenFrom(Map<String, Object> response) {
        Object token = response.get("access_token");
        if (token == null || String.valueOf(token).isBlank()) {
            throw new MeetingCredentialException("Provider token response did not include an access token");
        }
        String tokenType = String.valueOf(response.getOrDefault("token_type", "Bearer"));
        long expiresIn = parseExpiresIn(response.get("expires_in"));
        return new MeetingAccessToken(provider(), tokenType, String.valueOf(token), Instant.now().plusSeconds(expiresIn));
    }

    protected abstract com.justjava.humanresource.communication.meeting.MeetingProvider provider();

    private long parseExpiresIn(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            return Long.parseLong(String.valueOf(value));
        }
        return 3600L;
    }
}
