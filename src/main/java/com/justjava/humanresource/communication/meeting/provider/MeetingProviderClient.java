package com.justjava.humanresource.communication.meeting.provider;

import com.justjava.humanresource.communication.meeting.MeetingProvider;

public interface MeetingProviderClient {
    MeetingProvider provider();
    boolean isConfigured();
    CreatedMeeting createMeeting(CreateMeetingRequest request);
}
