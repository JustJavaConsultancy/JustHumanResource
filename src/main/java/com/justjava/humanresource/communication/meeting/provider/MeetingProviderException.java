package com.justjava.humanresource.communication.meeting.provider;

public class MeetingProviderException extends RuntimeException {
    public MeetingProviderException(String message) {
        super(message);
    }

    public MeetingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
