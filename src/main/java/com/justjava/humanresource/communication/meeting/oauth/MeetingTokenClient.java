package com.justjava.humanresource.communication.meeting.oauth;

import com.justjava.humanresource.communication.meeting.MeetingProvider;

public interface MeetingTokenClient {
    MeetingProvider provider();
    boolean isConfigured();
    MeetingAccessToken fetchToken();
}
