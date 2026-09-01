package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.GroupChatMessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GroupChatMessageAttachmentRepository extends JpaRepository<GroupChatMessageAttachment, Long> {
    List<GroupChatMessageAttachment> findAllByOrderByUploadedAtDesc();
    List<GroupChatMessageAttachment> findByMessage_IdInOrderByUploadedAtAsc(Collection<Long> messageIds);
    List<GroupChatMessageAttachment> findByMessage_IdOrderByUploadedAtAsc(Long messageId);
}
