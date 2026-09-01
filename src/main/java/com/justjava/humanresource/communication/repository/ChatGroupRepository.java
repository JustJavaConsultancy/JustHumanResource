package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.ChatGroup;
import com.justjava.humanresource.communication.entity.ChatGroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {

    @Query("""
            SELECT DISTINCT g
            FROM ChatGroup g
            JOIN ChatGroupMember m ON m.chatGroup = g
            WHERE m.employee.id = :employeeId
              AND m.removedAt IS NULL
              AND g.status = :status
            ORDER BY COALESCE(g.lastMessageAt, g.updatedAt) DESC
            """)
    List<ChatGroup> findActiveGroupsForEmployee(@Param("employeeId") Long employeeId,
                                                @Param("status") ChatGroupStatus status);

    List<ChatGroup> findByStatusOrderByUpdatedAtDesc(ChatGroupStatus status);
}
