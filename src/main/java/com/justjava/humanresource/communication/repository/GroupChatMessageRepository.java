package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.ChatGroup;
import com.justjava.humanresource.communication.entity.GroupChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, Long> {

    @Query("SELECT gcm FROM GroupChatMessage gcm WHERE gcm.group.id = :groupId ORDER BY gcm.createdAt DESC")
    Page<GroupChatMessage> findByGroupIdOrderByCreatedAtDesc(@Param("groupId") Long groupId, Pageable pageable);

    @Query("SELECT gcm FROM GroupChatMessage gcm WHERE gcm.group.id = :groupId AND gcm.createdAt > :since ORDER BY gcm.createdAt ASC")
    List<GroupChatMessage> findByGroupIdAndCreatedAfter(@Param("groupId") Long groupId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(gcm) FROM GroupChatMessage gcm WHERE gcm.group.id = :groupId AND gcm.readAt IS NULL AND gcm.sender.id != :employeeId")
    long countUnreadByGroupIdAndEmployeeId(@Param("groupId") Long groupId, @Param("employeeId") Long employeeId);

    @Query("SELECT gcm FROM GroupChatMessage gcm WHERE gcm.group.id = :groupId AND gcm.readAt IS NULL AND gcm.sender.id != :employeeId ORDER BY gcm.createdAt ASC")
    java.util.List<GroupChatMessage> findUnreadByGroupIdAndEmployeeId(@Param("groupId") Long groupId, @Param("employeeId") Long employeeId);

    @Query("SELECT gcm FROM GroupChatMessage gcm WHERE gcm.group.id = :groupId ORDER BY gcm.createdAt ASC")
    List<GroupChatMessage> findByGroupIdOrderByCreatedAtAsc(@Param("groupId") Long groupId);

    @Query("SELECT MAX(gcm.createdAt) FROM GroupChatMessage gcm WHERE gcm.group.id = :groupId")
    java.time.LocalDateTime findLastMessageTimeByGroupId(@Param("groupId") Long groupId);
}