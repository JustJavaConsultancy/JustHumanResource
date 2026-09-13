package com.justjava.humanresource.communication.websocket;

import com.justjava.humanresource.communication.dto.GroupMessageCommand;
import com.justjava.humanresource.communication.dto.GroupMessageResponse;
import com.justjava.humanresource.communication.entity.ChatGroup;
import com.justjava.humanresource.communication.entity.GroupChatMessage;
// ...existing imports...
import com.justjava.humanresource.communication.repository.ChatGroupMemberRepository;
import com.justjava.humanresource.communication.repository.ChatGroupRepository;
import com.justjava.humanresource.communication.repository.GroupChatMessageRepository;
import com.justjava.humanresource.communication.service.CommunicationService;
import com.justjava.humanresource.communication.service.GroupChatService;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GroupChatWebSocketHandler {

    private final GroupChatService groupChatService;
    private final CommunicationService communicationService;
    private final ChatGroupRepository chatGroupRepository;
    private final ChatGroupMemberRepository chatGroupMemberRepository;
    private final EmployeeRepository employeeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/groups.message")
    public void sendGroupMessage(@Payload GroupMessageCommand command,
                                  Principal principal) {
        // command contains groupId in the message body
        Long groupId = command.getGroupId();
        Employee sender = communicationService.employeeFromPrincipal(principal);

        GroupChatMessage message = groupChatService.sendMessage(groupId, sender.getId(), command);
        GroupMessageResponse response = groupChatService.toMessageResponse(message);

        // Broadcast to group topic (matching UI subscription: /topic/chat-groups/{groupId}/messages)
        messagingTemplate.convertAndSend("/topic/chat-groups/" + groupId + "/messages", response);

        // Send confirmation to sender (use chat-groups user queue to match topic prefix)
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/chat-groups/" + groupId + "/messages", response);
    }

    @MessageMapping("/groups/{groupId}/markRead")
    public void markMessagesAsRead(@DestinationVariable Long groupId, Principal principal) {
        Employee employee = communicationService.employeeFromPrincipal(principal);

        groupChatService.markMessagesAsRead(groupId, employee.getId());

        // Notify group of read receipt (use consistent "chat-groups" topic prefix)
        messagingTemplate.convertAndSend("/topic/chat-groups/" + groupId + "/read",
                new ReadReceiptEvent(groupId, employee.getId(), LocalDateTime.now()));
    }

    @MessageMapping("/groups/{groupId}/join")
    public void joinGroup(@DestinationVariable Long groupId, Principal principal) {
        Employee employee = communicationService.employeeFromPrincipal(principal);

        // Verify membership
        ChatGroup group = chatGroupRepository.findByIdAndStatus(groupId, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Group not found"));

        boolean isMember = chatGroupMemberRepository.existsByGroupIdAndEmployeeId(groupId, employee.getId());
        boolean isHrCreator = group.getCreatedByEmail().equals(principal.getName());

        if (!isMember && !isHrCreator) {
            throw new AccessDeniedException("Not a member of this group");
        }

        // Notify group of join (use consistent "chat-groups" topic prefix)
        messagingTemplate.convertAndSend("/topic/chat-groups/" + groupId + "/presence",
                new PresenceEvent(groupId, employee.getId(), employee.getFirstName() + " " + employee.getLastName(), "JOINED"));
    }

    @MessageMapping("/groups/{groupId}/leave")
    public void leaveGroup(@DestinationVariable Long groupId, Principal principal) {
        Employee employee = communicationService.employeeFromPrincipal(principal);

        // Notify group of leave (use consistent "chat-groups" topic prefix)
        messagingTemplate.convertAndSend("/topic/chat-groups/" + groupId + "/presence",
                new PresenceEvent(groupId, employee.getId(), employee.getFirstName() + " " + employee.getLastName(), "LEFT"));
    }

    @MessageMapping("/groups/{groupId}/typing")
    public void typingIndicator(@DestinationVariable Long groupId, Principal principal) {
        Employee employee = communicationService.employeeFromPrincipal(principal);

        // Verify membership
        ChatGroup group = chatGroupRepository.findByIdAndStatus(groupId, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Group not found"));

        boolean isMember = chatGroupMemberRepository.existsByGroupIdAndEmployeeId(groupId, employee.getId());
        boolean isHrCreator = group.getCreatedByEmail().equals(principal.getName());

        if (!isMember && !isHrCreator) {
            throw new AccessDeniedException("Not a member of this group");
        }

        // Broadcast typing indicator to others in group (use consistent "chat-groups" topic prefix)
        messagingTemplate.convertAndSend("/topic/chat-groups/" + groupId + "/typing",
                new TypingEvent(groupId, employee.getId(), employee.getFirstName() + " " + employee.getLastName()));
    }

    // Inner classes for events
    public record ReadReceiptEvent(Long groupId, Long employeeId, LocalDateTime readAt) {}
    public record PresenceEvent(Long groupId, Long employeeId, String employeeName, String action) {}
    public record TypingEvent(Long groupId, Long employeeId, String employeeName) {}
}