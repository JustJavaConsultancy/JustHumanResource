package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByParticipantOne_IdAndParticipantTwo_Id(Long participantOneId, Long participantTwoId);

    @Query("""
            SELECT c
            FROM Conversation c
            WHERE c.participantOne.id = :employeeId OR c.participantTwo.id = :employeeId
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findForEmployee(@Param("employeeId") Long employeeId);
}
