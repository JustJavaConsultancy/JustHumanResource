package com.justjava.humanresource.communication.service;

import com.justjava.humanresource.communication.dto.ChatGroupResponse;
import com.justjava.humanresource.communication.dto.CreateChatGroupCommand;
import com.justjava.humanresource.communication.dto.GroupMessageAttachmentResponse;
import com.justjava.humanresource.communication.dto.GroupMessageCommand;
import com.justjava.humanresource.communication.dto.GroupMessageResponse;
import com.justjava.humanresource.communication.entity.ChatGroup;
import com.justjava.humanresource.communication.entity.ChatGroupMember;
import com.justjava.humanresource.communication.entity.ChatGroupMemberRole;
import com.justjava.humanresource.communication.entity.GroupChatMessage;
import com.justjava.humanresource.communication.entity.GroupChatMessageAttachment;
import com.justjava.humanresource.communication.repository.ChatGroupMemberRepository;
import com.justjava.humanresource.communication.repository.ChatGroupRepository;
import com.justjava.humanresource.communication.repository.GroupChatMessageAttachmentRepository;
import com.justjava.humanresource.communication.repository.GroupChatMessageRepository;
import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.core.exception.UnauthorizedException;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Transactional
public class GroupChatService {

    private final ChatGroupRepository chatGroupRepository;
    private final ChatGroupMemberRepository chatGroupMemberRepository;
    private final GroupChatMessageRepository groupChatMessageRepository;
    private final GroupChatMessageAttachmentRepository groupChatMessageAttachmentRepository;
    private final EmployeeRepository employeeRepository;

    public ChatGroup createGroup(CreateChatGroupCommand command, String creatorEmail, String creatorName) {
        ChatGroup group = ChatGroup.builder()
                .name(command.getName())
                .description(command.getDescription())
                .createdByEmail(creatorEmail)
                .createdByName(creatorName)
                .build();

        ChatGroup savedGroup = chatGroupRepository.save(group);

        // Add members if provided
        if (command.getMemberEmployeeIds() != null && !command.getMemberEmployeeIds().isEmpty()) {
            List<Employee> members = employeeRepository.findAllById(command.getMemberEmployeeIds());
            for (Employee employee : members) {
                addMember(savedGroup.getId(), employee.getId(), com.justjava.humanresource.communication.entity.ChatGroupMemberRole.MEMBER);
            }
        }

        return savedGroup;
    }

    @Transactional(readOnly = true)
    public List<ChatGroupResponse> getGroupsForEmployee(Long employeeId) {
        List<ChatGroup> groups = chatGroupRepository.findActiveGroupsByEmployeeId(employeeId);
        return groups.stream()
                .map(group -> {
                    boolean isAdmin = chatGroupMemberRepository.existsByGroupIdAndEmployeeIdAndRole(
                            group.getId(), employeeId, com.justjava.humanresource.communication.entity.ChatGroupMemberRole.ADMIN);
                    boolean isMember = chatGroupMemberRepository.existsByGroupIdAndEmployeeId(group.getId(), employeeId);
                    int memberCount = (int) chatGroupMemberRepository.countByGroupId(group.getId());
                    GroupMessageResponse lastMessage = getLastMessage(group.getId());
                    return ChatGroupResponse.from(group, employeeId, isAdmin, isMember, memberCount, lastMessage);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatGroupResponse> getGroupsCreatedByHr(String hrEmail) {
        List<ChatGroup> groups = chatGroupRepository.findActiveGroupsByCreatorEmail(hrEmail);
        return groups.stream()
                .map(group -> {
                    boolean isAdmin = true; // HR creator is always admin
                    boolean isMember = true; // HR creator is a member
                    int memberCount = (int) chatGroupMemberRepository.countByGroupId(group.getId());
                    GroupMessageResponse lastMessage = getLastMessage(group.getId());
                    return ChatGroupResponse.from(group, null, isAdmin, isMember, memberCount, lastMessage);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChatGroupResponse getGroupDetails(Long groupId, Long employeeId) {
        ChatGroup group = chatGroupRepository.findByIdAndStatus(groupId, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ChatGroup"));

        boolean isMember = employeeId != null && chatGroupMemberRepository.existsByGroupIdAndEmployeeId(groupId, employeeId);
        boolean isAdmin = employeeId != null && chatGroupMemberRepository.existsByGroupIdAndEmployeeIdAndRole(
                groupId, employeeId, com.justjava.humanresource.communication.entity.ChatGroupMemberRole.ADMIN);

        // HR creators can also access groups they created
        if (!isMember && employeeId != null) {
            Employee employee = employeeRepository.findById(employeeId).orElse(null);
            if (employee != null && group.getCreatedByEmail().equals(employee.getEmail())) {
                isMember = true;
                isAdmin = true;
            }
        }

        if (!isMember && employeeId != null) {
            throw new UnauthorizedException("You are not a member of this group");
        }

        GroupMessageResponse lastMessage = getLastMessage(groupId);
        int memberCount = (int) chatGroupMemberRepository.countByGroupId(groupId);

        return ChatGroupResponse.from(group, employeeId, isAdmin, isMember, memberCount, lastMessage);
    }

    public ChatGroupMember addMember(Long groupId, Long employeeId, ChatGroupMemberRole role) {
        ChatGroup group = chatGroupRepository.findByIdAndStatus(groupId, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ChatGroup"));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee"));

        if (chatGroupMemberRepository.existsByGroupIdAndEmployeeId(groupId, employeeId)) {
            throw new IllegalStateException("Employee is already a member of this group");
        }

        ChatGroupMember member = ChatGroupMember.builder()
                .group(group)
                .employee(employee)
                .role(role)
                .build();

        return chatGroupMemberRepository.save(member);
    }

    public void removeMember(Long groupId, Long employeeId, Long requesterId) {
        ChatGroup group = chatGroupRepository.findByIdAndStatus(groupId, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ChatGroup"));

        // Check if requester is admin or HR creator
        boolean isRequesterAdmin = chatGroupMemberRepository.existsByGroupIdAndEmployeeIdAndRole(groupId, requesterId, com.justjava.humanresource.communication.entity.ChatGroupMemberRole.ADMIN);
        boolean isHrCreator = false;
        Employee requester = employeeRepository.findById(requesterId).orElse(null);
        if (requester != null) {
            isHrCreator = group.getCreatedByEmail().equals(requester.getEmail());
        }

        if (!isRequesterAdmin && !isHrCreator && !requesterId.equals(employeeId)) {
            throw new UnauthorizedException("Only admins or the HR creator can remove members");
        }

        chatGroupMemberRepository.deleteByGroupIdAndEmployeeId(groupId, employeeId);
    }

    public GroupChatMessage sendMessage(Long groupId, Long senderId, GroupMessageCommand command) {
        ChatGroup group = chatGroupRepository.findByIdAndStatus(groupId, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ChatGroup"));

        // Verify sender is a member (or HR creator)
        Employee sender = employeeRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee"));

        boolean isMember = chatGroupMemberRepository.existsByGroupIdAndEmployeeId(groupId, senderId);
        boolean isHrCreator = group.getCreatedByEmail().equals(sender.getEmail());

        if (!isMember && !isHrCreator) {
            throw new UnauthorizedException("Only group members can send messages");
        }

        GroupChatMessage message = GroupChatMessage.builder()
                .group(group)
                .sender(sender)
                .content(command.getContent())
                .build();

        GroupChatMessage savedMessage = groupChatMessageRepository.save(message);

        // Update group updatedAt
        group.setUpdatedAt(LocalDateTime.now());
        chatGroupRepository.save(group);

        return savedMessage;
    }

    @Transactional(readOnly = true)
    public List<GroupMessageResponse> getMessages(Long groupId, Long employeeId) {
        ChatGroup group = chatGroupRepository.findByIdAndStatus(groupId, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ChatGroup"));

        // Verify employee is a member (or HR creator)
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee"));

        boolean isMember = chatGroupMemberRepository.existsByGroupIdAndEmployeeId(groupId, employeeId);
        boolean isHrCreator = group.getCreatedByEmail().equals(employee.getEmail());

        if (!isMember && !isHrCreator) {
            throw new UnauthorizedException("Only group members can view messages");
        }

        List<GroupChatMessage> messages = groupChatMessageRepository.findByGroupIdOrderByCreatedAtAsc(groupId);
        return messages.stream().map(this::toMessageResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GroupMessageResponse> getMessagesSince(Long groupId, Long employeeId, LocalDateTime since) {
        ChatGroup group = chatGroupRepository.findByIdAndStatus(groupId, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ChatGroup"));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee"));

        boolean isMember = chatGroupMemberRepository.existsByGroupIdAndEmployeeId(groupId, employeeId);
        boolean isHrCreator = group.getCreatedByEmail().equals(employee.getEmail());

        if (!isMember && !isHrCreator) {
            throw new UnauthorizedException("Only group members can view messages");
        }

        List<GroupChatMessage> messages = groupChatMessageRepository.findByGroupIdAndCreatedAfter(groupId, since);
        return messages.stream().map(this::toMessageResponse).collect(Collectors.toList());
    }

    public GroupChatMessageAttachment addAttachment(Long messageId, Long employeeId, String originalFilename, String storedFilename,
                                                     String storagePath, String contentType, Long fileSize) {
        GroupChatMessage message = groupChatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupChatMessage"));

        // Verify employee is a member of the group
        if (!chatGroupMemberRepository.existsByGroupIdAndEmployeeId(message.getGroup().getId(), employeeId)) {
            throw new UnauthorizedException("Only group members can add attachments");
        }

        GroupChatMessageAttachment attachment = GroupChatMessageAttachment.builder()
                .message(message)
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .storagePath(storagePath)
                .contentType(contentType)
                .fileSize(fileSize)
                .uploadedByEmployeeId(employeeId)
                .build();

        return groupChatMessageAttachmentRepository.save(attachment);
    }

    @Transactional(readOnly = true)
    public List<GroupMessageAttachmentResponse> getAttachments(Long groupId, Long employeeId) {
        ChatGroup group = chatGroupRepository.findByIdAndStatus(groupId, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ChatGroup"));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee"));

        boolean isMember = chatGroupMemberRepository.existsByGroupIdAndEmployeeId(groupId, employeeId);
        boolean isHrCreator = group.getCreatedByEmail().equals(employee.getEmail());

        if (!isMember && !isHrCreator) {
            throw new UnauthorizedException("Only group members can view attachments");
        }

        List<GroupChatMessageAttachment> attachments = groupChatMessageAttachmentRepository.findByGroupId(groupId);
        return attachments.stream().map(this::toAttachmentResponse).collect(Collectors.toList());
    }

    public void markMessagesAsRead(Long groupId, Long employeeId) {
        // Mark unread messages in the group as read for the requesting employee.
        // Note: messages have a single readAt timestamp (not per-user). The existing
        // domain model treats readAt as a global flag, so we update unread messages
        // (where readAt IS NULL and sender != employeeId) to now.
        java.util.List<GroupChatMessage> unread = groupChatMessageRepository.findUnreadByGroupIdAndEmployeeId(groupId, employeeId);
        if (unread == null || unread.isEmpty()) {
            return;
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (GroupChatMessage m : unread) {
            m.setReadAt(now);
        }

        groupChatMessageRepository.saveAll(unread);
    }

    public GroupMessageResponse toMessageResponse(GroupChatMessage message) {
        GroupMessageResponse response = GroupMessageResponse.from(message);

        List<GroupChatMessageAttachment> attachments = groupChatMessageAttachmentRepository.findByMessageId(message.getId());
        response.setAttachments(attachments.stream().map(this::toAttachmentResponse).collect(Collectors.toList()));

        return response;
    }

    private GroupMessageAttachmentResponse toAttachmentResponse(GroupChatMessageAttachment attachment) {
        String uploadedByName = null;
        if (attachment.getUploadedByEmployeeId() != null) {
            Optional<Employee> employee = employeeRepository.findById(attachment.getUploadedByEmployeeId());
            uploadedByName = employee.map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
        }

        return GroupMessageAttachmentResponse.builder()
                .id(attachment.getId())
                .originalFilename(attachment.getOriginalFilename())
                .storedFilename(attachment.getStoredFilename())
                .storagePath(attachment.getStoragePath())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .uploadedByEmployeeId(attachment.getUploadedByEmployeeId())
                .uploadedByName(uploadedByName)
                .uploadedAt(attachment.getUploadedAt())
                .build();
    }

    @Transactional(readOnly = true)
    private GroupMessageResponse getLastMessage(Long groupId) {
        List<GroupChatMessage> messages = groupChatMessageRepository.findByGroupIdOrderByCreatedAtAsc(groupId);
        if (messages.isEmpty()) {
            return null;
        }
        // Return the last (most recent) message
        return toMessageResponse(messages.get(messages.size() - 1));
    }
}