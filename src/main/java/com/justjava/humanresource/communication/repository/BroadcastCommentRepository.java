package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.BroadcastComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BroadcastCommentRepository extends JpaRepository<BroadcastComment, Long> {
    List<BroadcastComment> findByBroadcast_IdOrderByCreatedAtAsc(Long broadcastId);
    long countByBroadcast_Id(Long broadcastId);
}
