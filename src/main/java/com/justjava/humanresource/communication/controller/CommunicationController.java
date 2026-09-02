package com.justjava.humanresource.communication.controller;

import com.justjava.humanresource.communication.dto.BroadcastCommentCommand;
import com.justjava.humanresource.communication.dto.BroadcastCommentResponse;
import com.justjava.humanresource.communication.dto.BroadcastCommand;
import com.justjava.humanresource.communication.dto.BroadcastResponse;
import com.justjava.humanresource.communication.dto.ChatGroupResponse;
import com.justjava.humanresource.communication.dto.ChatMessageResponse;
import com.justjava.humanresource.communication.dto.ConversationResponse;
import com.justjava.humanresource.communication.dto.CreateChatGroupCommand;
import com.justjava.humanresource.communication.dto.EmployeeContactResponse;
import com.justjava.humanresource.communication.dto.GroupMessageResponse;
import com.justjava.humanresource.communication.dto.PresenceResponse;
import com.justjava.humanresource.communication.entity.ChatMessageAttachment;
import com.justjava.humanresource.communication.entity.GroupChatMessageAttachment;
import com.justjava.humanresource.communication.entity.HrBroadcastAttachment;
import com.justjava.humanresource.communication.service.CommunicationAttachmentService;
import com.justjava.humanresource.communication.service.CommunicationService;
import com.justjava.humanresource.communication.service.GroupChatService;
import com.justjava.humanresource.communication.service.PresenceService;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class CommunicationController {

    private final CommunicationService communicationService;
    private final GroupChatService groupChatService;
    private final PresenceService presenceService;
    private final CommunicationAttachmentService attachmentService;
    private final AuthenticationManager authenticationManager;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/employee/communication")
    public String employeeCommunication(Model model) {
        Employee employee = communicationService.getCurrentEmployee();
        model.addAttribute("employee", employee);
        model.addAttribute("currentEmployeeId", employee.getId());
        model.addAttribute("currentEmployeeNumber", employee.getEmployeeNumber());
        model.addAttribute("canCreateGroups", groupChatService.canCreateGroups());
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
        model.addAttribute("canCreateGroups", groupChatService.canCreateGroups());
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
            model.addAttribute("currentEmployeeId", null);
            model.addAttribute("currentEmployeeNumber", "");
            model.addAttribute("directChatAvailable", false);
        }
        model.addAttribute("title", "Communication");
        model.addAttribute("subTitle", "Send HR broadcasts and monitor employee feedback");
        model.addAttribute("isRestrictedHr", authenticationManager.isRestrictedHr());
        model.addAttribute("canCreateGroups", groupChatService.canCreateGroups());
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

    @GetMapping({"/employee/communication/groups", "/mobile/employee/communication/groups", "/communication/groups"})
    @ResponseBody
    public List<ChatGroupResponse> groups() {
        return groupChatService.listMyGroups();
    }

    @PostMapping({"/employee/communication/groups", "/mobile/employee/communication/groups", "/communication/groups"})
    @ResponseBody
    public ResponseEntity<ChatGroupResponse> createGroup(@Valid @RequestBody CreateChatGroupCommand command) {
        ChatGroupResponse response = groupChatService.createGroup(command);
        messagingTemplate.convertAndSend("/topic/chat-groups", response);
        for (String employeeNumber : groupChatService.activeMemberEmployeeNumbers(response.id())) {
            messagingTemplate.convertAndSendToUser(employeeNumber, "/queue/group-notifications", response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/employee/communication/groups/available-members", "/mobile/employee/communication/groups/available-members", "/communication/groups/available-members"})
    @ResponseBody
    public List<EmployeeContactResponse> availableGroupMembers() {
        return groupChatService.availableMembers();
    }

    @GetMapping({"/employee/communication/groups/{groupId}/messages", "/mobile/employee/communication/groups/{groupId}/messages", "/communication/groups/{groupId}/messages"})
    @ResponseBody
    public List<GroupMessageResponse> groupMessages(@PathVariable Long groupId) {
        return groupChatService.getGroupMessages(groupId);
    }

    @PostMapping(value = {"/employee/communication/groups/{groupId}/messages", "/mobile/employee/communication/groups/{groupId}/messages"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<GroupMessageResponse> sendGroupMessageWithAttachments(
            @PathVariable Long groupId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) List<MultipartFile> files) {
        GroupMessageResponse response = groupChatService.sendMessageWithAttachments(groupId, content, files);
        messagingTemplate.convertAndSend("/topic/chat-groups/" + response.groupId() + "/messages", response);
        for (String employeeNumber : groupChatService.activeMemberEmployeeNumbers(response.groupId())) {
            messagingTemplate.convertAndSendToUser(
                    employeeNumber,
                    "/queue/group-notifications",
                    groupChatService.getGroupSummary(response.groupId())
            );
        }
        messagingTemplate.convertAndSend("/topic/chat-groups", groupChatService.getGroupSummary(response.groupId()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/employee/communication/groups/messages/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadGroupAttachment(@PathVariable Long messageId, @PathVariable Long attachmentId) {
        groupChatService.getReadableMessage(messageId);
        GroupChatMessageAttachment attachment = attachmentService.getGroupAttachment(messageId, attachmentId);
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), false, attachment.getStoragePath());
    }

    @GetMapping("/employee/communication/groups/messages/{messageId}/attachments/{attachmentId}/view")
    public ResponseEntity<Resource> viewGroupAttachment(@PathVariable Long messageId, @PathVariable Long attachmentId) {
        groupChatService.getReadableMessage(messageId);
        GroupChatMessageAttachment attachment = attachmentService.getGroupAttachment(messageId, attachmentId);
        return attachmentResponse(attachment.getContentType(), attachment.getFileSize(), attachment.getOriginalFilename(), true, attachment.getStoragePath());
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
