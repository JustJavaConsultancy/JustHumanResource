package com.justjava.humanresource.communication.meeting.provider;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.apps.meet.v2.SpacesServiceClient;
import com.google.apps.meet.v2.SpacesServiceSettings;
import com.google.auth.oauth2.UserCredentials;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GoogleMeetClientFactory {

    public SpacesServiceClient create(MeetingIntegrationProperties.Google google) {
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(google.getClientId())
                .setClientSecret(google.getClientSecret())
                .setRefreshToken(google.getRefreshToken())
                .build();

        try {
            SpacesServiceSettings settings = SpacesServiceSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
            return SpacesServiceClient.create(settings);
        } catch (IOException ex) {
            throw new MeetingProviderException("Could not create Google Meet client", ex);
        }
    }
}
