package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.GroupChatMessage;
import com.justjava.humanresource.communication.entity.GroupChatMessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupChatMessageAttachmentRepository extends JpaRepository<GroupChatMessageAttachment, Long> {

    @Query("SELECT gcmf FROM GroupChatMessageAttachment gcmf WHERE gcmf.message.id = :messageId")
    List<GroupChatMessageAttachment> findByMessageId(@Param("messageId") Long messageId);

    @Query("SELECT gcmf FROM GroupChatMessageAttachment gcmf WHERE gcmf.message.group.id = :groupId")
    List<GroupChatMessageAttachment> findByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT gcmf FROM GroupChatMessageAttachment gcmf ORDER BY gcmf.uploadedAt DESC")
    List<GroupChatMessageAttachment> findAllByOrderByUploadedAtDesc();
}