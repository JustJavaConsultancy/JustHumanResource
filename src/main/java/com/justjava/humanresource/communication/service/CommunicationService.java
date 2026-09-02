package com.justjava.humanresource.communication.service;

import com.justjava.humanresource.communication.dto.BroadcastCommand;
import com.justjava.humanresource.communication.dto.BroadcastCommentCommand;
import com.justjava.humanresource.communication.dto.BroadcastCommentResponse;
import com.justjava.humanresource.communication.dto.BroadcastResponse;
import com.justjava.humanresource.communication.dto.ChatMessageResponse;
import com.justjava.humanresource.communication.dto.CommunicationAttachmentResponse;
import com.justjava.humanresource.communication.dto.ConversationResponse;
import com.justjava.humanresource.communication.dto.DirectMessageCommand;
import com.justjava.humanresource.communication.dto.EmployeeContactResponse;
import com.justjava.humanresource.communication.entity.BroadcastComment;
import com.justjava.humanresource.communication.entity.ChatMessage;
import com.justjava.humanresource.communication.entity.ChatMessageAttachment;
import com.justjava.humanresource.communication.entity.Conversation;
import com.justjava.humanresource.communication.entity.HrBroadcast;
import com.justjava.humanresource.communication.entity.HrBroadcastAttachment;
import com.justjava.humanresource.communication.entity.HrBroadcastReceipt;
import com.justjava.humanresource.communication.entity.HrBroadcastStatus;
import com.justjava.humanresource.communication.repository.BroadcastCommentRepository;
import com.justjava.humanresource.communication.repository.ChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.ChatMessageRepository;
import com.justjava.humanresource.communication.repository.ConversationRepository;
import com.justjava.humanresource.communication.repository.HrBroadcastReceiptRepository;
import com.justjava.humanresource.communication.repository.HrBroadcastRepository;
import com.justjava.humanresource.communication.repository.HrBroadcastAttachmentRepository;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunicationService {

    private final AuthenticationManager authenticationManager;
    private final EmployeeRepository employeeRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageAttachmentRepository chatMessageAttachmentRepository;
    private final HrBroadcastRepository broadcastRepository;
    private final HrBroadcastAttachmentRepository broadcastAttachmentRepository;
    private final HrBroadcastReceiptRepository receiptRepository;
    private final BroadcastCommentRepository commentRepository;
    private final PresenceService presenceService;
    private final CommunicationAttachmentService attachmentService;

    @Transactional(readOnly = true)
    public Employee getCurrentEmployee() {
        Object email = authenticationManager.get("email");
        if (email == null || String.valueOf(email).isBlank()) {
            throw new AccessDeniedException("Authenticated user has no email claim");
        }
        return employeeRepository.findByEmail(String.valueOf(email))
                .orElseThrow(() -> new EntityNotFoundException("No employee profile found for " + email));
    }

    @Transactional(readOnly = true)
    public Employee employeeFromPrincipal(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Authenticated WebSocket principal is required");
        }
        String value = principal.getName();
        return employeeRepository.findByEmployeeNumber(value)
                .or(() -> employeeRepository.findByEmail(value))
                .orElseThrow(() -> new EntityNotFoundException("No employee profile found for " + value));
    }

    @Transactional(readOnly = true)
    public List<EmployeeContactResponse> listContacts() {
        Employee current = getCurrentEmployee();
        return listContactsFor(current);
    }

    @Transactional(readOnly = true)
    public List<EmployeeContactResponse> listContactsForHr() {
        assertHrCanMessageEmployees();
        return listContactsFor(getCurrentEmployee());
    }

    private List<EmployeeContactResponse> listContactsFor(Employee current) {
        Map<Long, Conversation> conversationsByEmployeeId = new HashMap<>();
        Map<Long, ChatMessage> lastMessagesByEmployeeId = new HashMap<>();

        for (Conversation conversation : conversationRepository.findForEmployee(current.getId())) {
            Employee other = otherParticipant(conversation, current);
            conversationsByEmployeeId.put(other.getId(), conversation);
            chatMessageRepository.findFirstByConversation_IdOrderByCreatedAtDesc(conversation.getId())
                    .ifPresent(message -> lastMessagesByEmployeeId.put(other.getId(), message));
        }

        return employeeRepository.findAllVisible().stream()
                .filter(employee -> !employee.getId().equals(current.getId()))
                .filter(this::isActiveEmployee)
                .map(employee -> toContact(
                        employee,
                        chatMessageRepository.countByRecipient_IdAndSender_IdAndReadAtIsNull(current.getId(), employee.getId()),
                        conversationsByEmployeeId.get(employee.getId()),
                        lastMessagesByEmployeeId.get(employee.getId())))
                .sorted(contactComparator())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations() {
        Employee current = getCurrentEmployee();
        return listConversationsFor(current);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversationsForHr() {
        assertHrCanMessageEmployees();
        return listConversationsFor(getCurrentEmployee());
    }

    private List<ConversationResponse> listConversationsFor(Employee current) {
        return conversationRepository.findForEmployee(current.getId()).stream()
                .map(conversation -> {
                    Employee other = otherParticipant(conversation, current);
                    ChatMessage lastMessage = chatMessageRepository
                            .findFirstByConversation_IdOrderByCreatedAtDesc(conversation.getId())
                            .orElse(null);
                    long unread = chatMessageRepository.countByRecipient_IdAndSender_IdAndReadAtIsNull(current.getId(), other.getId());
                    return new ConversationResponse(
                            conversation.getId(),
                            toContact(other, unread, conversation, lastMessage),
                            lastMessage == null ? null : toMessageResponse(lastMessage),
                            conversation.getLastMessageAt() == null ? conversation.getUpdatedAt() : conversation.getLastMessageAt());
                })
                .toList();
    }

    @Transactional
    public ChatMessageResponse sendDirectMessage(DirectMessageCommand command, Principal principal) {
        Employee sender = principal == null ? getCurrentEmployee() : employeeFromPrincipal(principal);
        Employee recipient = employeeRepository.findById(command.recipientEmployeeId())
                .filter(this::isActiveEmployee)
                .filter(employee -> !employee.isRestrictedVisibility())
                .orElseThrow(() -> new EntityNotFoundException("Recipient not found"));
        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("You cannot send a message to yourself");
        }

        Conversation conversation = getOrCreateConversation(sender, recipient);
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(normalizeContent(command.content(), 2000));
        if (presenceService.isOnline(recipient.getId())) {
            message.setDeliveredAt(LocalDateTime.now());
        }
        ChatMessage saved = chatMessageRepository.save(message);
        conversation.setLastMessageAt(saved.getCreatedAt() == null ? LocalDateTime.now() : saved.getCreatedAt());
        conversationRepository.save(conversation);
        return toMessageResponse(saved);
    }

    @Transactional
    public ChatMessageResponse sendDirectMessageWithAttachments(Long recipientEmployeeId, String content, List<MultipartFile> files) {
        Employee sender = getCurrentEmployee();
        return sendDirectMessageWithAttachments(sender, recipientEmployeeId, content, files);
    }

    @Transactional
    public ChatMessageResponse sendHrDirectMessageWithAttachments(Long recipientEmployeeId, String content, List<MultipartFile> files) {
        assertHrCanMessageEmployees();
        return sendDirectMessageWithAttachments(getCurrentEmployee(), recipientEmployeeId, content, files);
    }

    private ChatMessageResponse sendDirectMessageWithAttachments(Employee sender, Long recipientEmployeeId, String content, List<MultipartFile> files) {
        Employee recipient = employeeRepository.findById(recipientEmployeeId)
                .filter(this::isActiveEmployee)
                .filter(employee -> !employee.isRestrictedVisibility())
                .orElseThrow(() -> new EntityNotFoundException("Recipient not found"));
        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("You cannot send a message to yourself");
        }

        boolean hasFiles = hasFiles(files);
        Conversation conversation = getOrCreateConversation(sender, recipient);
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(normalizeContent(content, 2000, hasFiles));
        if (presenceService.isOnline(recipient.getId())) {
            message.setDeliveredAt(LocalDateTime.now());
        }
        ChatMessage saved = chatMessageRepository.save(message);
        List<ChatMessageAttachment> attachments = attachmentService.storeDirectAttachments(saved, files, sender.getId());
        conversation.setLastMessageAt(saved.getCreatedAt() == null ? LocalDateTime.now() : saved.getCreatedAt());
        conversationRepository.save(conversation);
        return toMessageResponse(saved, attachments);
    }

    @Transactional
    public ChatMessageResponse sendSystemDirectMessageToEmployee(Long senderEmployeeId, Long recipientEmployeeId, String content) {
        Employee sender = employeeRepository.findById(senderEmployeeId)
                .filter(this::isActiveEmployee)
                .orElseThrow(() -> new EntityNotFoundException("Meeting notification sender not found"));
        Employee recipient = employeeRepository.findById(recipientEmployeeId)
                .filter(this::isActiveEmployee)
                .filter(employee -> !employee.isRestrictedVisibility())
                .orElseThrow(() -> new EntityNotFoundException("Recipient not found"));
        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("Meeting notification sender cannot receive its own direct message");
        }

        Conversation conversation = getOrCreateConversation(sender, recipient);
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(normalizeContent(content, 2000));
        if (presenceService.isOnline(recipient.getId())) {
            message.setDeliveredAt(LocalDateTime.now());
        }
        ChatMessage saved = chatMessageRepository.save(message);
        conversation.setLastMessageAt(saved.getCreatedAt() == null ? LocalDateTime.now() : saved.getCreatedAt());
        conversationRepository.save(conversation);
        return toMessageResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getConversationMessages(Long conversationId) {
        Employee current = getCurrentEmployee();
        return getConversationMessagesFor(conversationId, current);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getHrConversationMessages(Long conversationId) {
        assertHrCanMessageEmployees();
        return getConversationMessagesFor(conversationId, getCurrentEmployee());
    }

    private List<ChatMessageResponse> getConversationMessagesFor(Long conversationId, Employee current) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));
        assertConversationParticipant(conversation, current);
        List<ChatMessage> messages = chatMessageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
        Map<Long, List<ChatMessageAttachment>> attachmentsByMessageId = chatMessageAttachmentRepository
                .findByMessage_IdInOrderByUploadedAtAsc(messages.stream().map(ChatMessage::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getMessage().getId()));
        return messages.stream()
                .map(message -> toMessageResponse(message, attachmentsByMessageId.getOrDefault(message.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatMessage getReadableMessage(Long messageId) {
        Employee current = getCurrentEmployee();
        return getReadableMessageFor(messageId, current);
    }

    @Transactional(readOnly = true)
    public ChatMessage getHrReadableMessage(Long messageId) {
        assertHrCanMessageEmployees();
        return getReadableMessageFor(messageId, getCurrentEmployee());
    }

    private ChatMessage getReadableMessageFor(Long messageId, Employee current) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found"));
        assertConversationParticipant(message.getConversation(), current);
        return message;
    }

    @Transactional
    public BroadcastResponse createBroadcast(BroadcastCommand command) {
        assertHrCanBroadcast();
        HrBroadcast broadcast = new HrBroadcast();
        broadcast.setTitle(normalizeContent(command.title(), 160));
        broadcast.setContent(normalizeContent(command.content(), 5000));
        broadcast.setCreatedByEmail(String.valueOf(authenticationManager.get("email")));
        Object name = authenticationManager.get("name");
        broadcast.setCreatedByName(name == null ? "HR" : String.valueOf(name));
        HrBroadcast saved = broadcastRepository.save(broadcast);

        for (Long employeeId : presenceService.onlineEmployeeIds()) {
            employeeRepository.findById(employeeId).ifPresent(employee -> markBroadcastDelivered(saved, employee));
        }
        return toBroadcastResponse(saved, null);
    }

    @Transactional
    public BroadcastResponse createBroadcastWithAttachments(String title, String content, List<MultipartFile> files) {
        assertHrCanBroadcast();
        HrBroadcast broadcast = new HrBroadcast();
        broadcast.setTitle(normalizeContent(title, 160));
        broadcast.setContent(normalizeContent(content, 5000));
        broadcast.setCreatedByEmail(String.valueOf(authenticationManager.get("email")));
        Object name = authenticationManager.get("name");
        broadcast.setCreatedByName(name == null ? "HR" : String.valueOf(name));
        HrBroadcast saved = broadcastRepository.save(broadcast);
        List<HrBroadcastAttachment> attachments = attachmentService.storeBroadcastAttachments(
                saved,
                files,
                saved.getCreatedByEmail()
        );

        for (Long employeeId : presenceService.onlineEmployeeIds()) {
            employeeRepository.findById(employeeId).ifPresent(employee -> markBroadcastDelivered(saved, employee));
        }
        return toBroadcastResponse(saved, null, attachments);
    }

    @Transactional(readOnly = true)
    public List<BroadcastResponse> listBroadcastsForEmployee() {
        Employee current = getCurrentEmployee();
        List<HrBroadcast> broadcasts = broadcastRepository.findByStatusOrderByCreatedAtDesc(HrBroadcastStatus.ACTIVE);
        Map<Long, List<HrBroadcastAttachment>> attachmentsByBroadcastId = attachmentsByBroadcastId(broadcasts);
        return broadcasts.stream()
                .map(broadcast -> toBroadcastResponse(broadcast, current, attachmentsByBroadcastId.getOrDefault(broadcast.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BroadcastResponse> listBroadcastsForHr() {
        assertHrCanBroadcast();
        List<HrBroadcast> broadcasts = broadcastRepository.findByStatusOrderByCreatedAtDesc(HrBroadcastStatus.ACTIVE);
        Map<Long, List<HrBroadcastAttachment>> attachmentsByBroadcastId = attachmentsByBroadcastId(broadcasts);
        return broadcasts.stream()
                .map(broadcast -> toBroadcastResponse(broadcast, null, attachmentsByBroadcastId.getOrDefault(broadcast.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BroadcastResponse getBroadcastSummary(Long broadcastId) {
        HrBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new EntityNotFoundException("Broadcast not found"));
        return toBroadcastResponse(
                broadcast,
                null,
                broadcastAttachmentRepository.findByBroadcast_IdOrderByUploadedAtAsc(broadcastId)
        );
    }

    @Transactional(readOnly = true)
    public HrBroadcast getReadableBroadcast(Long broadcastId) {
        HrBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .filter(item -> item.getStatus() == HrBroadcastStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Active broadcast not found"));
        if (!isHrOrAdmin()) {
            getCurrentEmployee();
        }
        return broadcast;
    }

    @Transactional
    public List<BroadcastResponse> deliverUndeliveredBroadcasts(Employee employee) {
        return broadcastRepository.findByStatusOrderByCreatedAtDesc(HrBroadcastStatus.ACTIVE).stream()
                .filter(broadcast -> receiptRepository.findByBroadcast_IdAndEmployee_Id(broadcast.getId(), employee.getId()).isEmpty())
                .map(broadcast -> {
                    markBroadcastDelivered(broadcast, employee);
                    return toBroadcastResponse(broadcast, employee);
                })
                .toList();
    }

    @Transactional
    public void markBroadcastDelivered(HrBroadcast broadcast, Employee employee) {
        HrBroadcastReceipt receipt = receiptRepository.findByBroadcast_IdAndEmployee_Id(broadcast.getId(), employee.getId())
                .orElseGet(() -> {
                    HrBroadcastReceipt created = new HrBroadcastReceipt();
                    created.setBroadcast(broadcast);
                    created.setEmployee(employee);
                    return created;
                });
        if (receipt.getDeliveredAt() == null) {
            receipt.setDeliveredAt(LocalDateTime.now());
        }
        receiptRepository.save(receipt);
    }

    @Transactional
    public BroadcastResponse markBroadcastRead(Long broadcastId) {
        Employee current = getCurrentEmployee();
        HrBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new EntityNotFoundException("Broadcast not found"));
        HrBroadcastReceipt receipt = receiptRepository.findByBroadcast_IdAndEmployee_Id(broadcastId, current.getId())
                .orElseGet(() -> {
                    HrBroadcastReceipt created = new HrBroadcastReceipt();
                    created.setBroadcast(broadcast);
                    created.setEmployee(current);
                    return created;
                });
        if (receipt.getDeliveredAt() == null) {
            receipt.setDeliveredAt(LocalDateTime.now());
        }
        receipt.setReadAt(LocalDateTime.now());
        receiptRepository.save(receipt);
        return toBroadcastResponse(broadcast, current);
    }

    @Transactional
    public BroadcastCommentResponse addBroadcastComment(BroadcastCommentCommand command, Principal principal) {
        Employee employee = principal == null ? getCurrentEmployee() : employeeFromPrincipal(principal);
        HrBroadcast broadcast = broadcastRepository.findById(command.broadcastId())
                .filter(item -> item.getStatus() == HrBroadcastStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Active broadcast not found"));
        BroadcastComment comment = new BroadcastComment();
        comment.setBroadcast(broadcast);
        comment.setEmployee(employee);
        comment.setContent(normalizeContent(command.content(), 2000));
        return toCommentResponse(commentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public List<BroadcastCommentResponse> getBroadcastComments(Long broadcastId) {
        return commentRepository.findByBroadcast_IdOrderByCreatedAtAsc(broadcastId).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    private Conversation getOrCreateConversation(Employee first, Employee second) {
        Employee participantOne = first.getId() < second.getId() ? first : second;
        Employee participantTwo = first.getId() < second.getId() ? second : first;
        return conversationRepository.findByParticipantOne_IdAndParticipantTwo_Id(participantOne.getId(), participantTwo.getId())
                .orElseGet(() -> conversationRepository.save(new Conversation(participantOne, participantTwo)));
    }

    private void assertConversationParticipant(Conversation conversation, Employee employee) {
        Set<Long> participantIds = new LinkedHashSet<>(List.of(
                conversation.getParticipantOne().getId(),
                conversation.getParticipantTwo().getId()
        ));
        if (!participantIds.contains(employee.getId())) {
            throw new AccessDeniedException("You cannot read this conversation");
        }
    }

    private Employee otherParticipant(Conversation conversation, Employee current) {
        if (conversation.getParticipantOne().getId().equals(current.getId())) {
            return conversation.getParticipantTwo();
        }
        return conversation.getParticipantOne();
    }

    private boolean isActiveEmployee(Employee employee) {
        return employee.getEmploymentStatus() == EmploymentStatus.ACTIVE;
    }

    private String normalizeContent(String value, int maxLength) {
        return normalizeContent(value, maxLength, false);
    }

    private String normalizeContent(String value, int maxLength, boolean allowBlank) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() && !allowBlank) {
            throw new IllegalArgumentException("Message content is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Message content is too long");
        }
        return normalized;
    }

    private void assertHrCanBroadcast() {
        if (!isHrOrAdmin()) {
            throw new AccessDeniedException("Only HR or admin users can send broadcasts");
        }
    }

    private void assertHrCanMessageEmployees() {
        if (!isHrOrAdmin()) {
            throw new AccessDeniedException("Only HR or admin users can message employees from the HR console");
        }
    }

    private boolean isHrOrAdmin() {
        return authenticationManager.isHumanResource() || authenticationManager.isAdmin();
    }

    private EmployeeContactResponse toContact(Employee employee, long unreadCount, Conversation conversation, ChatMessage lastMessage) {
        return new EmployeeContactResponse(
                employee.getId(),
                employee.getEmployeeNumber(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getDepartment() == null ? "Unassigned" : employee.getDepartment().getName(),
                presenceService.isOnline(employee.getId()),
                unreadCount,
                conversation == null ? null : conversation.getId(),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                conversation != null
        );
    }

    private Comparator<EmployeeContactResponse> contactComparator() {
        return Comparator
                .comparing(EmployeeContactResponse::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(EmployeeContactResponse::online, Comparator.reverseOrder())
                .thenComparing(EmployeeContactResponse::fullName, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return toMessageResponse(message, List.of());
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message, List<ChatMessageAttachment> attachments) {
        return new ChatMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getEmployeeNumber(),
                message.getSender().getFullName(),
                message.getRecipient().getId(),
                message.getRecipient().getEmployeeNumber(),
                message.getContent(),
                message.getDeliveredAt(),
                message.getReadAt(),
                message.getCreatedAt(),
                attachments.stream().map(this::toAttachmentResponse).toList()
        );
    }

    private CommunicationAttachmentResponse toAttachmentResponse(ChatMessageAttachment attachment) {
        Long messageId = attachment.getMessage().getId();
        return new CommunicationAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                "/employee/communication/messages/" + messageId + "/attachments/" + attachment.getId() + "/view",
                "/employee/communication/messages/" + messageId + "/attachments/" + attachment.getId()
        );
    }

    private boolean hasFiles(List<MultipartFile> files) {
        return Optional.ofNullable(files).orElse(List.of()).stream().anyMatch(file -> file != null && !file.isEmpty());
    }

    private Map<Long, List<HrBroadcastAttachment>> attachmentsByBroadcastId(List<HrBroadcast> broadcasts) {
        List<Long> ids = broadcasts.stream().map(HrBroadcast::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return broadcastAttachmentRepository.findByBroadcast_IdInOrderByUploadedAtAsc(ids).stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getBroadcast().getId()));
    }

    private BroadcastResponse toBroadcastResponse(HrBroadcast broadcast, Employee employee) {
        return toBroadcastResponse(
                broadcast,
                employee,
                broadcastAttachmentRepository.findByBroadcast_IdOrderByUploadedAtAsc(broadcast.getId())
        );
    }

    private BroadcastResponse toBroadcastResponse(HrBroadcast broadcast, Employee employee, List<HrBroadcastAttachment> attachments) {
        boolean read = employee != null && receiptRepository.isReadByEmployee(broadcast.getId(), employee.getId());
        return new BroadcastResponse(
                broadcast.getId(),
                broadcast.getTitle(),
                broadcast.getContent(),
                broadcast.getCreatedByName(),
                broadcast.getCreatedByEmail(),
                broadcast.getStatus().name(),
                broadcast.getCreatedAt(),
                receiptRepository.countByBroadcast_IdAndDeliveredAtIsNotNull(broadcast.getId()),
                receiptRepository.countByBroadcast_IdAndReadAtIsNotNull(broadcast.getId()),
                commentRepository.countByBroadcast_Id(broadcast.getId()),
                read,
                attachments.stream().map(this::toAttachmentResponse).toList()
        );
    }

    private CommunicationAttachmentResponse toAttachmentResponse(HrBroadcastAttachment attachment) {
        Long broadcastId = attachment.getBroadcast().getId();
        return new CommunicationAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                "/employee/communication/broadcasts/" + broadcastId + "/attachments/" + attachment.getId() + "/view",
                "/employee/communication/broadcasts/" + broadcastId + "/attachments/" + attachment.getId()
        );
    }

    private BroadcastCommentResponse toCommentResponse(BroadcastComment comment) {
        Employee employee = comment.getEmployee();
        return new BroadcastCommentResponse(
                comment.getId(),
                comment.getBroadcast().getId(),
                employee.getId(),
                employee.getFullName(),
                employee.getDepartment() == null ? "Unassigned" : employee.getDepartment().getName(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
