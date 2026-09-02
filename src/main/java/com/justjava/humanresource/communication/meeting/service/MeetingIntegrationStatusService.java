package com.justjava.humanresource.communication.meeting.service;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import com.justjava.humanresource.communication.meeting.dto.MeetingIntegrationStatusResponse;
import com.justjava.humanresource.communication.meeting.oauth.MeetingTokenClient;
import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MeetingIntegrationStatusService {

    private final AuthenticationManager authenticationManager;
    private final MeetingIntegrationProperties properties;
    private final List<MeetingTokenClient> tokenClients;

    public List<MeetingIntegrationStatusResponse> listStatuses() {
        requireHrOrAdmin();
        Map<MeetingProvider, MeetingTokenClient> clients = new EnumMap<>(MeetingProvider.class);
        tokenClients.forEach(client -> clients.put(client.provider(), client));
        return List.of(
                        zoomStatus(clients.get(MeetingProvider.ZOOM)),
                        microsoftStatus(clients.get(MeetingProvider.MICROSOFT_TEAMS)),
                        googleStatus(clients.get(MeetingProvider.GOOGLE_MEET))
                )
                .stream()
                .sorted(Comparator.comparing(status -> status.provider().name()))
                .toList();
    }

    private MeetingIntegrationStatusResponse zoomStatus(MeetingTokenClient client) {
        MeetingIntegrationProperties.Zoom zoom = properties.getProviders().getZoom();
        return new MeetingIntegrationStatusResponse(
                MeetingProvider.ZOOM,
                zoom.isEnabled(),
                client != null && client.isConfigured(),
                blankToNull(zoom.getDefaultHostUserId()),
                zoom.getApiBaseUrl()
        );
    }

    private MeetingIntegrationStatusResponse microsoftStatus(MeetingTokenClient client) {
        MeetingIntegrationProperties.Microsoft microsoft = properties.getProviders().getMicrosoft();
        return new MeetingIntegrationStatusResponse(
                MeetingProvider.MICROSOFT_TEAMS,
                microsoft.isEnabled(),
                client != null && client.isConfigured(),
                blankToNull(microsoft.getDefaultOrganizerUserId()),
                microsoft.getGraphBaseUrl()
        );
    }

    private MeetingIntegrationStatusResponse googleStatus(MeetingTokenClient client) {
        MeetingIntegrationProperties.Google google = properties.getProviders().getGoogle();
        return new MeetingIntegrationStatusResponse(
                MeetingProvider.GOOGLE_MEET,
                google.isEnabled(),
                client != null && client.isConfigured(),
                blankToNull(google.getDefaultOrganizerEmail()),
                google.getMeetBaseUrl()
        );
    }

    private void requireHrOrAdmin() {
        if (!(authenticationManager.isHumanResource() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("Only HR and admin users can manage meeting integrations.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
