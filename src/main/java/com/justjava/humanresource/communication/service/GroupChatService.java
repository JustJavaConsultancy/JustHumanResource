package com.justjava.humanresource.communication.service;

import com.justjava.humanresource.communication.dto.ChatGroupResponse;
import com.justjava.humanresource.communication.dto.CommunicationAttachmentResponse;
import com.justjava.humanresource.communication.dto.CreateChatGroupCommand;
import com.justjava.humanresource.communication.dto.EmployeeContactResponse;
import com.justjava.humanresource.communication.dto.GroupMessageCommand;
import com.justjava.humanresource.communication.dto.GroupMessageResponse;
import com.justjava.humanresource.communication.entity.ChatGroup;
import com.justjava.humanresource.communication.entity.ChatGroupMember;
import com.justjava.humanresource.communication.entity.ChatGroupMemberRole;
import com.justjava.humanresource.communication.entity.ChatGroupStatus;
import com.justjava.humanresource.communication.entity.GroupChatMessage;
import com.justjava.humanresource.communication.entity.GroupChatMessageAttachment;
import com.justjava.humanresource.communication.repository.ChatGroupMemberRepository;
import com.justjava.humanresource.communication.repository.ChatGroupRepository;
import com.justjava.humanresource.communication.repository.GroupChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.GroupChatMessageRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupChatService {

    private final AuthenticationManager authenticationManager;
    private final EmployeeRepository employeeRepository;
    private final ChatGroupRepository chatGroupRepository;
    private final ChatGroupMemberRepository memberRepository;
    private final GroupChatMessageRepository messageRepository;
    private final GroupChatMessageAttachmentRepository attachmentRepository;
    private final PresenceService presenceService;
    private final CommunicationService communicationService;
    private final CommunicationAttachmentService attachmentService;

    @Transactional(readOnly = true)
    public boolean canCreateGroups() {
        if (isHrOrAdmin()) {
            return true;
        }
        return currentEmployeeOptional() != null && isDepartmentHead(currentEmployeeOptional());
    }

    @Transactional(readOnly = true)
    public List<EmployeeContactResponse> availableMembers() {
        // Check HR/Admin FIRST - they don't need currentEmployeeOptional()
        if (isHrOrAdmin()) {
            return employeeRepository.findAllVisible().stream()
                    .filter(this::isActiveEmployee)
                    .map(employee -> toContact(employee))
                    .sorted(Comparator.comparing(EmployeeContactResponse::fullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        }

        // For non-HR/Admin, get current employee (might throw if no profile)
        Employee current = currentEmployeeOptional();
        if (current == null || !isDepartmentHead(current)) {
            // Return empty list instead of throwing exception - user can see the form but no members
            return List.of();
        }
        if (current.getDepartment() == null) {
            // Return empty list instead of throwing exception
            return List.of();
        }
        Long departmentId = current.getDepartment().getId();
        return employeeRepository.findAllVisible().stream()
                .filter(this::isActiveEmployee)
                .filter(employee -> employee.getDepartment() != null && employee.getDepartment().getId().equals(departmentId))
                .map(employee -> toContact(employee))
                .sorted(Comparator.comparing(EmployeeContactResponse::fullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Transactional
    public ChatGroupResponse createGroup(CreateChatGroupCommand command) {
        Employee creator = currentEmployeeOptional();
        boolean hr = isHrOrAdmin();
        if (!hr && (creator == null || !isDepartmentHead(creator))) {
            throw new AccessDeniedException("Only HR or department heads can create groups");
        }
        if (!hr && creator.getDepartment() == null) {
            throw new IllegalArgumentException("No department is assigned to your employee profile");
        }

        Set<Long> memberIds = new LinkedHashSet<>();
        if (command.memberEmployeeIds() != null) {
            memberIds.addAll(command.memberEmployeeIds());
        }
        if (creator != null) {
            memberIds.add(creator.getId());
        }
        if (memberIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one group member");
        }

        List<Employee> members = employeeRepository.findAllById(memberIds).stream()
                .filter(this::isActiveEmployee)
                .filter(employee -> !employee.isRestrictedVisibility())
                .toList();
        if (members.size() != memberIds.size()) {
            throw new IllegalArgumentException("One or more selected members are not available");
        }
        if (!hr) {
            Long departmentId = creator.getDepartment().getId();
            boolean hasOutsideDepartment = members.stream()
                    .anyMatch(employee -> employee.getDepartment() == null || !employee.getDepartment().getId().equals(departmentId));
            if (hasOutsideDepartment) {
                throw new AccessDeniedException("Department heads can only add employees in their department");
            }
        }

        ChatGroup group = new ChatGroup();
        group.setName(normalize(command.name(), 100));
        group.setDescription(blankToNull(command.description(), 500));
        group.setDepartment(!hr && creator != null ? creator.getDepartment() : null);
        group.setCreatedByEmployee(creator);
        Object email = authenticationManager.get("email");
        group.setCreatedByEmail(email == null ? null : String.valueOf(email));
        group.setCreatedByRole(hr ? "HR" : "HOD");
        ChatGroup saved = chatGroupRepository.save(group);

        for (Employee member : members) {
            addMember(saved, member, creator != null && creator.getId().equals(member.getId()) ? ChatGroupMemberRole.OWNER : ChatGroupMemberRole.MEMBER);
        }
        return toGroupResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatGroupResponse> listMyGroups() {
        if (isHrOrAdmin()) {
            return chatGroupRepository.findByStatusOrderByUpdatedAtDesc(ChatGroupStatus.ACTIVE).stream()
                    .map(this::toGroupResponse)
                    .toList();
        }
        Employee current = communicationService.getCurrentEmployee();
        return chatGroupRepository.findActiveGroupsForEmployee(current.getId(), ChatGroupStatus.ACTIVE).stream()
                .map(this::toGroupResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupMessageResponse> getGroupMessages(Long groupId) {
        Employee current = communicationService.getCurrentEmployee();
        assertCanReadGroup(groupId, current);
        List<GroupChatMessage> messages = messageRepository.findByChatGroup_IdOrderByCreatedAtAsc(groupId);
        Map<Long, List<GroupChatMessageAttachment>> attachmentsByMessageId = attachmentRepository
                .findByMessage_IdInOrderByUploadedAtAsc(messages.stream().map(GroupChatMessage::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getMessage().getId()));
        return messages.stream()
                .map(message -> toMessageResponse(message, attachmentsByMessageId.getOrDefault(message.getId(), List.of())))
                .toList();
    }

    @Transactional
    public GroupMessageResponse sendMessage(GroupMessageCommand command, Principal principal) {
        Employee sender = principal == null ? communicationService.getCurrentEmployee() : communicationService.employeeFromPrincipal(principal);
        ChatGroup group = chatGroupRepository.findById(command.groupId())
                .filter(item -> item.getStatus() == ChatGroupStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
        assertCanReadGroup(group.getId(), sender);

        GroupChatMessage message = new GroupChatMessage();
        message.setChatGroup(group);
        message.setSender(sender);
        message.setContent(normalize(command.content(), 2000));
        GroupChatMessage saved = messageRepository.save(message);
        group.setLastMessageAt(saved.getCreatedAt() == null ? LocalDateTime.now() : saved.getCreatedAt());
        chatGroupRepository.save(group);
        return toMessageResponse(saved);
    }

    @Transactional
    public GroupMessageResponse sendMessageWithAttachments(Long groupId, String content, List<MultipartFile> files) {
        Employee sender = communicationService.getCurrentEmployee();
        ChatGroup group = chatGroupRepository.findById(groupId)
                .filter(item -> item.getStatus() == ChatGroupStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
        assertCanReadGroup(group.getId(), sender);

        GroupChatMessage message = new GroupChatMessage();
        message.setChatGroup(group);
        message.setSender(sender);
        message.setContent(normalize(content, 2000, hasFiles(files)));
        GroupChatMessage saved = messageRepository.save(message);
        List<GroupChatMessageAttachment> attachments = attachmentService.storeGroupAttachments(saved, files, sender.getId());
        group.setLastMessageAt(saved.getCreatedAt() == null ? LocalDateTime.now() : saved.getCreatedAt());
        chatGroupRepository.save(group);
        return toMessageResponse(saved, attachments);
    }

    @Transactional(readOnly = true)
    public GroupChatMessage getReadableMessage(Long messageId) {
        Employee current = communicationService.getCurrentEmployee();
        GroupChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found"));
        assertCanReadGroup(message.getChatGroup().getId(), current);
        return message;
    }

    @Transactional(readOnly = true)
    public ChatGroupResponse getGroupSummary(Long groupId) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
        return toGroupResponse(group);
    }

    @Transactional(readOnly = true)
    public List<String> activeMemberEmployeeNumbers(Long groupId) {
        return memberRepository.findByChatGroup_IdAndRemovedAtIsNullOrderByEmployee_LastNameAscEmployee_FirstNameAsc(groupId)
                .stream()
                .map(member -> member.getEmployee().getEmployeeNumber())
                .filter(number -> number != null && !number.isBlank())
                .toList();
    }

    private void addMember(ChatGroup group, Employee employee, ChatGroupMemberRole role) {
        ChatGroupMember member = memberRepository.findByChatGroup_IdAndEmployee_Id(group.getId(), employee.getId())
                .orElseGet(ChatGroupMember::new);
        member.setChatGroup(group);
        member.setEmployee(employee);
        member.setRole(role);
        member.setJoinedAt(member.getJoinedAt() == null ? LocalDateTime.now() : member.getJoinedAt());
        member.setRemovedAt(null);
        memberRepository.save(member);
    }

    private void assertCanReadGroup(Long groupId, Employee employee) {
        if (isHrOrAdmin()) {
            return;
        }
        if (!memberRepository.existsByChatGroup_IdAndEmployee_IdAndRemovedAtIsNull(groupId, employee.getId())) {
            throw new AccessDeniedException("You are not a member of this group");
        }
    }

    private Employee currentEmployeeOptional() {
        try {
            return communicationService.getCurrentEmployee();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isHrOrAdmin() {
        return authenticationManager.isHumanResource() || authenticationManager.isAdmin();
    }

    private boolean isDepartmentHead(Employee employee) {
        if (employee.getDepartment() != null
                && employee.getDepartment().getDepartmentHead() != null
                && employee.getDepartment().getDepartmentHead().getId().equals(employee.getId())) {
            return true;
        }
        return employee.getGroups() != null && employee.getGroups().stream()
                .map(group -> group == null ? "" : group.replace("/", "").toLowerCase(Locale.ROOT))
                .anyMatch(group -> group.equals("departmenthead"));
    }

    private boolean isActiveEmployee(Employee employee) {
        return employee.getEmploymentStatus() == EmploymentStatus.ACTIVE;
    }

    private String normalize(String value, int maxLength) {
        return normalize(value, maxLength, false);
    }

    private String normalize(String value, int maxLength, boolean allowBlank) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() && !allowBlank) {
            throw new IllegalArgumentException("Value is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Value is too long");
        }
        return normalized;
    }

    private String blankToNull(String value, int maxLength) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return normalize(value, maxLength);
    }

    private EmployeeContactResponse toContact(Employee employee) {
        return new EmployeeContactResponse(
                employee.getId(),
                employee.getEmployeeNumber(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getDepartment() == null ? "Unassigned" : employee.getDepartment().getName(),
                presenceService.isOnline(employee.getId()),
                0,
                null,
                null,
                null,
                false
        );
    }

    private ChatGroupResponse toGroupResponse(ChatGroup group) {
        GroupChatMessage lastMessage = messageRepository.findFirstByChatGroup_IdOrderByCreatedAtDesc(group.getId()).orElse(null);
        String createdByName = group.getCreatedByEmployee() == null ? group.getCreatedByEmail() : group.getCreatedByEmployee().getFullName();
        return new ChatGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getDepartment() == null ? "All departments" : group.getDepartment().getName(),
                memberRepository.countByChatGroup_IdAndRemovedAtIsNull(group.getId()),
                createdByName == null ? "HR" : createdByName,
                group.getCreatedByRole(),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? group.getLastMessageAt() : lastMessage.getCreatedAt()
        );
    }

    private GroupMessageResponse toMessageResponse(GroupChatMessage message) {
        return toMessageResponse(message, List.of());
    }

    private GroupMessageResponse toMessageResponse(GroupChatMessage message, List<GroupChatMessageAttachment> attachments) {
        return new GroupMessageResponse(
                message.getId(),
                message.getChatGroup().getId(),
                message.getChatGroup().getName(),
                message.getSender().getId(),
                message.getSender().getEmployeeNumber(),
                message.getSender().getFullName(),
                message.getContent(),
                message.getCreatedAt(),
                attachments.stream().map(this::toAttachmentResponse).toList()
        );
    }

    private CommunicationAttachmentResponse toAttachmentResponse(GroupChatMessageAttachment attachment) {
        Long messageId = attachment.getMessage().getId();
        return new CommunicationAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                "/employee/communication/groups/messages/" + messageId + "/attachments/" + attachment.getId() + "/view",
                "/employee/communication/groups/messages/" + messageId + "/attachments/" + attachment.getId()
        );
    }

    private boolean hasFiles(List<MultipartFile> files) {
        return Optional.ofNullable(files).orElse(List.of()).stream().anyMatch(file -> file != null && !file.isEmpty());
    }
}
