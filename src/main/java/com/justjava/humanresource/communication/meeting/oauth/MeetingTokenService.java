package com.justjava.humanresource.communication.meeting.oauth;

import com.justjava.humanresource.communication.meeting.MeetingProvider;

public interface MeetingTokenService {
    MeetingAccessToken accessToken(MeetingProvider provider);
}
