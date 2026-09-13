package com.justjava.humanresource.communication.controller;

import com.justjava.humanresource.communication.dto.AvailableMemberResponse;
import com.justjava.humanresource.communication.dto.BroadcastCommentCommand;
import com.justjava.humanresource.communication.dto.BroadcastCommentResponse;
import com.justjava.humanresource.communication.dto.BroadcastCommand;
import com.justjava.humanresource.communication.dto.BroadcastResponse;
import com.justjava.humanresource.communication.dto.ChatGroupResponse;
import com.justjava.humanresource.communication.dto.ChatMessageResponse;
import com.justjava.humanresource.communication.dto.ConversationResponse;
import com.justjava.humanresource.communication.dto.CreateChatGroupCommand;
import com.justjava.humanresource.communication.dto.DirectMessageCommand;
import com.justjava.humanresource.communication.dto.EmployeeContactResponse;
import com.justjava.humanresource.communication.dto.GroupMessageAttachmentResponse;
import com.justjava.humanresource.communication.dto.GroupMessageCommand;
import com.justjava.humanresource.communication.dto.GroupMessageResponse;
import com.justjava.humanresource.communication.dto.PresenceResponse;
import com.justjava.humanresource.communication.entity.*;
import com.justjava.humanresource.communication.service.CommunicationAttachmentService;
import com.justjava.humanresource.communication.service.CommunicationService;
import com.justjava.humanresource.communication.service.GroupChatService;
import com.justjava.humanresource.communication.service.PresenceService;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.entity.Employee;
import java.security.Principal;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.justjava.humanresource.communication.repository.GroupChatMessageAttachmentRepository;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CommunicationController {

    private final CommunicationService communicationService;
    private final PresenceService presenceService;
    private final CommunicationAttachmentService attachmentService;
    private final GroupChatService groupChatService;
    private final GroupChatMessageAttachmentRepository groupChatMessageAttachmentRepository;
    private final AuthenticationManager authenticationManager;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/employee/communication")
    public String employeeCommunication(Model model) {
        Employee employee = communicationService.getCurrentEmployee();
        model.addAttribute("employee", employee);
        model.addAttribute("currentEmployeeId", employee.getId());
        model.addAttribute("currentEmployeeNumber", employee.getEmployeeNumber());
        model.addAttribute("canCreateGroups", false);
        model.addAttribute("title", "Communication");
        model.addAttribute("subTitle", "Chat with colleagues and follow HR broadcasts");
        return "employees/communication";
    }

    @GetMapping("/mobile/employee/communication")
    public String mobileEmployeeCommunication(Model model) {
        Employee employee = communicationService.getCurrentEmployee();
        model.addAttribute("employee", employee);
        model.addAttribute("currentEmployeeId", employee.getId());
        model.addAttribute("currentEmployeeNumber", employee.getEmployeeNumber());
        model.addAttribute("canCreateGroups", false);
        model.addAttribute("title", "Messages");
        model.addAttribute("subTitle", "Chats and HR updates");
        return "mobile/communication";
    }

    @GetMapping("/communication")
    public String hrCommunication(Model model) {
        try {
            Employee employee = communicationService.getCurrentEmployee();
            model.addAttribute("currentEmployeeId", employee.getId());
            model.addAttribute("currentEmployeeNumber", employee.getEmployeeNumber());
            model.addAttribute("directChatAvailable", true);
        } catch (EntityNotFoundException | AccessDeniedException exception) {
            // HR without employee profile - use HR system employee
            Employee hrSystem = communicationService.getOrCreateHrSystemEmployee("hr-system@company.local");
            model.addAttribute("currentEmployeeId", hrSystem.getId());
            model.addAttribute("currentEmployeeNumber", hrSystem.getEmployeeNumber());
            model.addAttribute("directChatAvailable", true);
        }
        model.addAttribute("title", "Communication");
        model.addAttribute("subTitle", "Send HR broadcasts and monitor employee feedback");
        model.addAttribute("isRestrictedHr", authenticationManager.isRestrictedHr());
        model.addAttribute("canCreateGroups", false);
        return "communication/main";
    }

    @GetMapping("/employee/communication/employees")
    @ResponseBody
    public List<EmployeeContactResponse> employeeContacts() {
        return communicationService.listContacts();
    }

    @GetMapping("/communication/employees")
    @ResponseBody
    public List<EmployeeContactResponse> hrEmployeeContacts() {
        return communicationService.listContactsForHr();
    }

    @GetMapping("/employee/communication/conversations")
    @ResponseBody
    public List<ConversationResponse> employeeConversations() {
        return communicationService.listConversations();
    }

    @GetMapping("/communication/conversations")
    @ResponseBody
    public List<ConversationResponse> hrConversations() {
        return communicationService.listConversationsForHr();
    }

    @GetMapping("/employee/communication/conversations/{conversationId}/messages")
    @ResponseBody
    public List<ChatMessageResponse> conversationMessages(@PathVariable Long conversationId) {
        return communicationService.getConversationMessages(conversationId);
    }

    @GetMapping("/communication/conversations/{conversationId}/messages")
    @ResponseBody
    public List<ChatMessageResponse> hrConversationMessages(@PathVariable Long conversationId) {
        return communicationService.getHrConversationMessages(conversationId);
    }

    @PostMapping(value = {"/employee/communication/messages", "/mobile/employee/communication/messages"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<ChatMessageResponse> sendDirectMessageWithAttachments(
            @RequestParam Long recipientEmployeeId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) List<MultipartFile> files) {
        ChatMessageResponse response = communicationService.sendDirectMessageWithAttachments(recipientEmployeeId, content, files);
        messagingTemplate.convertAndSendToUser(response.recipientEmployeeNumber(), "/queue/messages", response);
        if (response.recipientEmployeeNumber() != null && ("HR-SYSTEM".equalsIgnoreCase(response.recipientEmployeeNumber()) || "HR".equalsIgnoreCase(response.recipientEmployeeNumber()))) {
            messagingTemplate.convertAndSend("/topic/hr-inbox", response);
        }
        messagingTemplate.convertAndSendToUser(response.senderEmployeeNumber(), "/queue/messages", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/communication/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ChatMessageResponse> sendHrDirectMessageJson(
            @Valid @RequestBody DirectMessageCommand command, Principal principal) {
        ChatMessageResponse response = communicationService.sendDirectMessage(command, principal);
        messagingTemplate.convertAndSendToUser(response.recipientEmployeeNumber(), "/queue/messages", response);
        if (response.recipientEmployeeNumber() != null && ("HR-SYSTEM".equalsIgnoreCase(response.recipientEmployeeNumber()) || "HR".equalsIgnoreCase(response.recipientEmployeeNumber()))) {
            messagingTemplate.convertAndSend("/topic/hr-inbox", response);
        }
        messagingTemplate.convertAndSendToUser(response.senderEmployeeNumber(), "/queue/messages", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/communication/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<ChatMessageResponse> sendHrDirectMessageWithAttachments(
            @RequestParam Long recipientEmployeeId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) List<MultipartFile> files) {
        ChatMessageResponse response = communicationService.sendHrDirectMessageWithAttachments(recipientEmployeeId, content, files);
        messagingTemplate.convertAndSendToUser(response.recipientEmployeeNumber(), "/queue/messages", response);
        if (response.recipientEmployeeNumber() != null && ("HR-SYSTEM".equalsIgnoreCase(response.recipientEmployeeNumber()) || "HR".equalsIgnoreCase(response.recipientEmployeeNumber()))) {
            messagingTemplate.convertAndSend("/topic/hr-inbox", response);
        }
        messagingTemplate.convertAndSendToUser(response.senderEmployeeNumber(), "/queue/messages", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/employee/communication/messages/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadDirectAttachment(@PathVariable Long messageId, @PathVariable Long attachmentId) {
        communicationService.getReadableMessage(messageId);
        ChatMessageAttachment attachment = attachmentService.getDirectAttachment(messageId, attachmentId);
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), false, attachment.getStoragePath());
    }

    @GetMapping("/communication/messages/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadHrDirectAttachment(@PathVariable Long messageId, @PathVariable Long attachmentId) {
        communicationService.getHrReadableMessage(messageId);
        ChatMessageAttachment attachment = attachmentService.getDirectAttachment(messageId, attachmentId);
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), false, attachment.getStoragePath());
    }

    @GetMapping("/employee/communication/messages/{messageId}/attachments/{attachmentId}/view")
    public ResponseEntity<Resource> viewDirectAttachment(@PathVariable Long messageId, @PathVariable Long attachmentId) {
        communicationService.getReadableMessage(messageId);
        ChatMessageAttachment attachment = attachmentService.getDirectAttachment(messageId, attachmentId);
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), true, attachment.getStoragePath());
    }

    @GetMapping("/communication/messages/{messageId}/attachments/{attachmentId}/view")
    public ResponseEntity<Resource> viewHrDirectAttachment(@PathVariable Long messageId, @PathVariable Long attachmentId) {
        communicationService.getHrReadableMessage(messageId);
        ChatMessageAttachment attachment = attachmentService.getDirectAttachment(messageId, attachmentId);
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), true, attachment.getStoragePath());
    }

    @GetMapping("/employee/communication/broadcasts")
    @ResponseBody
    public List<BroadcastResponse> employeeBroadcasts() {
        return communicationService.listBroadcastsForEmployee();
    }

    @GetMapping("/communication/broadcasts")
    @ResponseBody
    public List<BroadcastResponse> hrBroadcasts() {
        return communicationService.listBroadcastsForHr();
    }

    @PostMapping(value = "/communication/broadcasts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<BroadcastResponse> createBroadcast(@Valid @RequestBody BroadcastCommand command) {
        BroadcastResponse response = communicationService.createBroadcast(command);
        messagingTemplate.convertAndSend("/topic/hr-broadcasts", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/communication/broadcasts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<BroadcastResponse> createBroadcastWithAttachments(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(required = false) List<MultipartFile> files) {
        BroadcastResponse response = communicationService.createBroadcastWithAttachments(title, content, files);
        messagingTemplate.convertAndSend("/topic/hr-broadcasts", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/employee/communication/broadcasts/{broadcastId}/comments", "/communication/broadcasts/{broadcastId}/comments"})
    @ResponseBody
    public List<BroadcastCommentResponse> broadcastComments(@PathVariable Long broadcastId) {
        return communicationService.getBroadcastComments(broadcastId);
    }

    @PostMapping("/employee/communication/broadcasts/{broadcastId}/read")
    @ResponseBody
    public ResponseEntity<BroadcastResponse> markBroadcastRead(@PathVariable Long broadcastId) {
        BroadcastResponse response = communicationService.markBroadcastRead(broadcastId);
        messagingTemplate.convertAndSend("/topic/hr-broadcasts/" + response.id() + "/receipts", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/employee/communication/broadcasts/{broadcastId}/comments")
    @ResponseBody
    public ResponseEntity<BroadcastCommentResponse> addComment(@PathVariable Long broadcastId,
                                                               @Valid @RequestBody BroadcastCommentCommand command) {
        BroadcastCommentResponse response = communicationService.addBroadcastComment(
                new BroadcastCommentCommand(broadcastId, command.content()),
                null
        );
        messagingTemplate.convertAndSend("/topic/hr-broadcasts/" + broadcastId + "/comments", response);
        messagingTemplate.convertAndSend("/topic/hr-broadcasts", communicationService.getBroadcastSummary(broadcastId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/employee/communication/presence")
    @ResponseBody
    public List<PresenceResponse> employeePresence() {
        return presenceService.getOnlineEmployees();
    }

    @GetMapping("/communication/presence")
    @ResponseBody
    public List<PresenceResponse> hrPresence() {
        return presenceService.getOnlineEmployees();
    }

    // ============ Available Members for Chat Groups ============

    @GetMapping("/communication/groups/available-members")
    @ResponseBody
    public List<AvailableMemberResponse> hrAvailableMembers() {
        return communicationService.getAvailableMembersForGroup();
    }

    @GetMapping("/employee/communication/groups/available-members")
    @ResponseBody
    public List<AvailableMemberResponse> employeeAvailableMembers() {
        return communicationService.getAvailableMembersForGroup();
    }

    @GetMapping("/mobile/employee/communication/groups/available-members")
    @ResponseBody
    public List<AvailableMemberResponse> mobileEmployeeAvailableMembers() {
        return communicationService.getAvailableMembersForGroup();
    }


    @GetMapping({"/employee/communication/broadcasts/{broadcastId}/attachments/{attachmentId}",
            "/communication/broadcasts/{broadcastId}/attachments/{attachmentId}"})
    public ResponseEntity<Resource> downloadBroadcastAttachment(@PathVariable Long broadcastId, @PathVariable Long attachmentId) {
        communicationService.getReadableBroadcast(broadcastId);
        HrBroadcastAttachment attachment = attachmentService.getBroadcastAttachment(broadcastId, attachmentId);
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), false, attachment.getStoragePath());
    }

    @GetMapping({"/employee/communication/broadcasts/{broadcastId}/attachments/{attachmentId}/view",
            "/communication/broadcasts/{broadcastId}/attachments/{attachmentId}/view"})
    public ResponseEntity<Resource> viewBroadcastAttachment(@PathVariable Long broadcastId, @PathVariable Long attachmentId) {
        communicationService.getReadableBroadcast(broadcastId);
        HrBroadcastAttachment attachment = attachmentService.getBroadcastAttachment(broadcastId, attachmentId);
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), true, attachment.getStoragePath());
    }

    // ============ Chat Group Endpoints ============

    @GetMapping("/employee/communication/groups")
    @ResponseBody
    public List<ChatGroupResponse> employeeGroups() {
        Employee employee = communicationService.getCurrentEmployee();
        return groupChatService.getGroupsForEmployee(employee.getId());
    }

    @GetMapping("/communication/groups")
    @ResponseBody
    public List<ChatGroupResponse> hrGroups() {
        // For HR, get groups they created (they may not have employee profile)
        return groupChatService.getGroupsCreatedByHr(authenticationManager.getCurrentUserEmail());
    }

    @GetMapping("/employee/communication/groups/{groupId}")
    @ResponseBody
    public ChatGroupResponse employeeGroupDetails(@PathVariable Long groupId) {
        Employee employee = communicationService.getCurrentEmployee();
        return groupChatService.getGroupDetails(groupId, employee.getId());
    }

    @GetMapping("/communication/groups/{groupId}")
    @ResponseBody
    public ChatGroupResponse hrGroupDetails(@PathVariable Long groupId) {
        Long employeeId = null;
        try {
            Employee employee = communicationService.getCurrentEmployee();
            employeeId = employee.getId();
        } catch (Exception e) {
            // HR without employee profile
        }
        return groupChatService.getGroupDetails(groupId, employeeId);
    }

    @PostMapping(value = "/communication/groups", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ChatGroupResponse> createGroup(@Valid @RequestBody CreateChatGroupCommand command) {
        String creatorEmail = authenticationManager.getCurrentUserEmail();
        String creatorName = authenticationManager.getCurrentUserName();
        ChatGroup group = groupChatService.createGroup(command, creatorEmail, creatorName);
        ChatGroupResponse response = ChatGroupResponse.from(group, null, true, true, 1, null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/communication/groups/{groupId}/members")
    @ResponseBody
    public ResponseEntity<Void> addMember(@PathVariable Long groupId, @RequestParam Long employeeId) {
        groupChatService.addMember(groupId, employeeId, com.justjava.humanresource.communication.entity.ChatGroupMemberRole.MEMBER);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/communication/groups/{groupId}/members/{employeeId}")
    @ResponseBody
    public ResponseEntity<Void> removeMember(@PathVariable Long groupId, @PathVariable Long employeeId) {
        Long requesterId = null;
        try {
            Employee employee = communicationService.getCurrentEmployee();
            requesterId = employee.getId();
        } catch (Exception e) {
            // HR without employee profile - need to get HR system employee ID
            Employee hrSystem = communicationService.getOrCreateHrSystemEmployee("hr-system@company.local");
            requesterId = hrSystem.getId();
        }
        groupChatService.removeMember(groupId, employeeId, requesterId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/employee/communication/groups/{groupId}/messages")
    @ResponseBody
    public List<GroupMessageResponse> employeeGroupMessages(@PathVariable Long groupId) {
        Employee employee = communicationService.getCurrentEmployee();
        return groupChatService.getMessages(groupId, employee.getId());
    }

    @GetMapping("/communication/groups/{groupId}/messages")
    @ResponseBody
    public List<GroupMessageResponse> hrGroupMessages(@PathVariable Long groupId) {
        Long employeeId = null;
        try {
            Employee employee = communicationService.getCurrentEmployee();
            employeeId = employee.getId();
        } catch (Exception e) {
            // HR without employee profile
        }
        return groupChatService.getMessages(groupId, employeeId);
    }

    @GetMapping("/employee/communication/groups/{groupId}/messages/since")
    @ResponseBody
    public List<GroupMessageResponse> employeeGroupMessagesSince(@PathVariable Long groupId,
                                                                  @RequestParam LocalDateTime since) {
        Employee employee = communicationService.getCurrentEmployee();
        return groupChatService.getMessagesSince(groupId, employee.getId(), since);
    }

    @GetMapping("/communication/groups/{groupId}/messages/since")
    @ResponseBody
    public List<GroupMessageResponse> hrGroupMessagesSince(@PathVariable Long groupId,
                                                            @RequestParam LocalDateTime since) {
        Long employeeId = null;
        try {
            Employee employee = communicationService.getCurrentEmployee();
            employeeId = employee.getId();
        } catch (Exception e) {
            // HR without employee profile
        }
        return groupChatService.getMessagesSince(groupId, employeeId, since);
    }

    @PostMapping(value = "/employee/communication/groups/{groupId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<GroupMessageResponse> sendEmployeeGroupMessage(@PathVariable Long groupId,
                                                                          @Valid @RequestBody GroupMessageCommand command) {
        Employee employee = communicationService.getCurrentEmployee();
        GroupChatMessage message = groupChatService.sendMessage(groupId, employee.getId(), command);
        GroupMessageResponse response = groupChatService.toMessageResponse(message);
        messagingTemplate.convertAndSend("/topic/chat-groups/" + groupId + "/messages", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/communication/groups/{groupId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<GroupMessageResponse> sendHrGroupMessage(@PathVariable Long groupId,
                                                                    @Valid @RequestBody GroupMessageCommand command) {
        Long employeeId = null;
        try {
            Employee employee = communicationService.getCurrentEmployee();
            employeeId = employee.getId();
        } catch (Exception e) {
            // HR without employee profile - use HR system employee
            Employee hrSystem = communicationService.getOrCreateHrSystemEmployee("hr-system@company.local");
            employeeId = hrSystem.getId();
        }
        GroupChatMessage message = groupChatService.sendMessage(groupId, employeeId, command);
        GroupMessageResponse response = groupChatService.toMessageResponse(message);
        messagingTemplate.convertAndSend("/topic/chat-groups/" + groupId + "/messages", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/employee/communication/groups/{groupId}/attachments")
    @ResponseBody
    public List<GroupMessageAttachmentResponse> employeeGroupAttachments(@PathVariable Long groupId) {
        Employee employee = communicationService.getCurrentEmployee();
        return groupChatService.getAttachments(groupId, employee.getId());
    }

    @GetMapping("/communication/groups/{groupId}/attachments")
    @ResponseBody
    public List<GroupMessageAttachmentResponse> hrGroupAttachments(@PathVariable Long groupId) {
        Long employeeId = null;
        try {
            Employee employee = communicationService.getCurrentEmployee();
            employeeId = employee.getId();
        } catch (Exception e) {
            // HR without employee profile
        }
        return groupChatService.getAttachments(groupId, employeeId);
    }

    @GetMapping("/employee/communication/groups/{groupId}/messages/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadEmployeeGroupAttachment(@PathVariable Long groupId,
                                                                     @PathVariable Long messageId,
                                                                     @PathVariable Long attachmentId) {
        Employee employee = communicationService.getCurrentEmployee();
        groupChatService.getGroupDetails(groupId, employee.getId()); // Verify access
        GroupChatMessageAttachment attachment = groupChatMessageAttachmentRepository.findByMessageId(messageId)
                .stream().filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), false, attachment.getStoragePath());
    }

    @GetMapping("/communication/groups/{groupId}/messages/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadHrGroupAttachment(@PathVariable Long groupId,
                                                               @PathVariable Long messageId,
                                                               @PathVariable Long attachmentId) {
        Long employeeId = null;
        try {
            Employee employee = communicationService.getCurrentEmployee();
            employeeId = employee.getId();
        } catch (Exception e) {
            // HR without employee profile
        }
        groupChatService.getGroupDetails(groupId, employeeId); // Verify access
        GroupChatMessageAttachment attachment = groupChatMessageAttachmentRepository.findByMessageId(messageId)
                .stream().filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), false, attachment.getStoragePath());
    }

    @GetMapping("/employee/communication/groups/{groupId}/messages/{messageId}/attachments/{attachmentId}/view")
    public ResponseEntity<Resource> viewEmployeeGroupAttachment(@PathVariable Long groupId,
                                                                 @PathVariable Long messageId,
                                                                 @PathVariable Long attachmentId) {
        Employee employee = communicationService.getCurrentEmployee();
        groupChatService.getGroupDetails(groupId, employee.getId()); // Verify access
        GroupChatMessageAttachment attachment = groupChatMessageAttachmentRepository.findByMessageId(messageId)
                .stream().filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), true, attachment.getStoragePath());
    }

    @GetMapping("/communication/groups/{groupId}/messages/{messageId}/attachments/{attachmentId}/view")
    public ResponseEntity<Resource> viewHrGroupAttachment(@PathVariable Long groupId,
                                                           @PathVariable Long messageId,
                                                           @PathVariable Long attachmentId) {
        Long employeeId = null;
        try {
            Employee employee = communicationService.getCurrentEmployee();
            employeeId = employee.getId();
        } catch (Exception e) {
            // HR without employee profile
        }
        groupChatService.getGroupDetails(groupId, employeeId); // Verify access
        GroupChatMessageAttachment attachment = groupChatMessageAttachmentRepository.findByMessageId(messageId)
                .stream().filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), true, attachment.getStoragePath());
    }

    private ResponseEntity<Resource> attachmentResponse(String contentType,
                                                       Long fileSize,
                                                       String filename,
                                                       boolean inline,
                                                       String storagePath) {
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(filename).build()
                : ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(attachmentService.load(storagePath));
    }
}
