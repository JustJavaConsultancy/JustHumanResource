package com.justjava.humanresource.communication.websocket;

import com.justjava.humanresource.communication.dto.BroadcastCommand;
import com.justjava.humanresource.communication.dto.BroadcastCommentCommand;
import com.justjava.humanresource.communication.dto.BroadcastCommentResponse;
import com.justjava.humanresource.communication.dto.BroadcastResponse;
import com.justjava.humanresource.communication.dto.ChatMessageResponse;
import com.justjava.humanresource.communication.dto.DirectMessageCommand;
import com.justjava.humanresource.communication.service.CommunicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CommunicationSocketController {

    private final CommunicationService communicationService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendDirectMessage(@Valid DirectMessageCommand command, Principal principal) {
        ChatMessageResponse response = communicationService.sendDirectMessage(command, principal);
        messagingTemplate.convertAndSendToUser(response.recipientEmployeeNumber(), "/queue/messages", response);
        messagingTemplate.convertAndSendToUser(response.senderEmployeeNumber(), "/queue/messages", response);
    }

    @MessageMapping("/broadcast.send")
    public void sendBroadcast(@Valid BroadcastCommand command) {
        BroadcastResponse response = communicationService.createBroadcast(command);
        messagingTemplate.convertAndSend("/topic/hr-broadcasts", response);
    }

    @MessageMapping("/broadcast.comment")
    public void commentOnBroadcast(@Valid BroadcastCommentCommand command, Principal principal) {
        BroadcastCommentResponse response = communicationService.addBroadcastComment(command, principal);
        messagingTemplate.convertAndSend(
                "/topic/hr-broadcasts/" + response.broadcastId() + "/comments",
                response
        );
    }

    @MessageMapping("/broadcasts/{broadcastId}/read")
    public void markRead(@DestinationVariable Long broadcastId) {
        BroadcastResponse response = communicationService.markBroadcastRead(broadcastId);
        messagingTemplate.convertAndSend("/topic/hr-broadcasts/" + response.id() + "/receipts", response);
    }
}
