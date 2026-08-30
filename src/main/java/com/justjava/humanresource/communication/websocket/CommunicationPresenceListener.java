package com.justjava.humanresource.communication.websocket;

import com.justjava.humanresource.communication.dto.BroadcastResponse;
import com.justjava.humanresource.communication.dto.PresenceResponse;
import com.justjava.humanresource.communication.service.CommunicationService;
import com.justjava.humanresource.communication.service.PresenceService;
import com.justjava.humanresource.hr.entity.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CommunicationPresenceListener {

    private final CommunicationService communicationService;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        Principal principal = event.getUser();
        String sessionId = sessionId(event);
        if (principal == null || sessionId == null) {
            return;
        }

        try {
            Employee employee = communicationService.employeeFromPrincipal(principal);
            PresenceResponse presence = presenceService.markOnline(employee, sessionId);
            messagingTemplate.convertAndSend("/topic/presence", presence);

            List<BroadcastResponse> undelivered = communicationService.deliverUndeliveredBroadcasts(employee);
            for (BroadcastResponse broadcast : undelivered) {
                messagingTemplate.convertAndSendToUser(employee.getEmployeeNumber(), "/queue/broadcasts", broadcast);
            }
        } catch (RuntimeException ignored) {
            // HR-only users without an employee record can still use the broadcast page.
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presenceService.markOffline(event.getSessionId())
                .ifPresent(presence -> messagingTemplate.convertAndSend("/topic/presence", presence));
    }

    private String sessionId(SessionConnectEvent event) {
        Object value = event.getMessage().getHeaders().get("simpSessionId");
        return value == null ? null : String.valueOf(value);
    }
}
