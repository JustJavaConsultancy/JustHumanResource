package com.justjava.humanresource.communication.meeting.config;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "meeting")
public class MeetingIntegrationProperties {

    private Defaults defaults = new Defaults();
    private Notifications notifications = new Notifications();
    private Providers providers = new Providers();

    @Getter
    @Setter
    public static class Defaults {
        private MeetingProvider provider = MeetingProvider.MICROSOFT_TEAMS;
        private Duration duration = Duration.ofMinutes(30);
        private String timezone = "Africa/Lagos";
        private boolean notifyParticipants = true;
        private boolean allowInstantMeeting = true;
    }

    @Getter
    @Setter
    public static class Notifications {
        private Long systemSenderEmployeeId;
        private boolean sendChatInvites = true;
        private boolean sendEmailInvites = true;
    }

    @Getter
    @Setter
    public static class Providers {
        private Zoom zoom = new Zoom();
        private Microsoft microsoft = new Microsoft();
        private Google google = new Google();
    }

    @Getter
    @Setter
    public static class Zoom {
        private boolean enabled;
        private String accountId;
        private String clientId;
        private String clientSecret;
        private String defaultHostUserId = "me";
        private String tokenUrl = "https://zoom.us/oauth/token";
        private String apiBaseUrl = "https://api.zoom.us/v2";
    }

    @Getter
    @Setter
    public static class Microsoft {
        private boolean enabled;
        private String tenantId;
        private String clientId;
        private String clientSecret;
        private String defaultOrganizerUserId;
        private String graphBaseUrl = "https://graph.microsoft.com/v1.0";
    }

    @Getter
    @Setter
    public static class Google {
        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String refreshToken;
        private String redirectUri;
        private String defaultOrganizerEmail;
        private String tokenUrl = "https://oauth2.googleapis.com/token";
        private String meetBaseUrl = "https://meet.googleapis.com/v2";
    }
}
