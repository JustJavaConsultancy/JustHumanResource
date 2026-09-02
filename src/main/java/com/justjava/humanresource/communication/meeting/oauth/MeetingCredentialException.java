package com.justjava.humanresource.communication.meeting.oauth;

public class MeetingCredentialException extends RuntimeException {
    public MeetingCredentialException(String message) {
        super(message);
    }

    public MeetingCredentialException(String message, Throwable cause) {
        super(message, cause);
    }
}
