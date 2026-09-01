package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.GroupChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, Long> {
    List<GroupChatMessage> findByChatGroup_IdOrderByCreatedAtAsc(Long groupId);
    Optional<GroupChatMessage> findFirstByChatGroup_IdOrderByCreatedAtDesc(Long groupId);
}
