package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.ChatMessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChatMessageAttachmentRepository extends JpaRepository<ChatMessageAttachment, Long> {
    List<ChatMessageAttachment> findAllByOrderByUploadedAtDesc();
    List<ChatMessageAttachment> findByMessage_IdInOrderByUploadedAtAsc(Collection<Long> messageIds);
    List<ChatMessageAttachment> findByMessage_IdOrderByUploadedAtAsc(Long messageId);
}
