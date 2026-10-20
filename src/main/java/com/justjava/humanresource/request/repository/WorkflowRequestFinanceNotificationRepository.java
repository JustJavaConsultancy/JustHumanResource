package com.justjava.humanresource.request.repository;

import com.justjava.humanresource.request.entity.WorkflowRequestFinanceNotification;
import com.justjava.humanresource.request.enums.FinanceNotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WorkflowRequestFinanceNotificationRepository extends JpaRepository<WorkflowRequestFinanceNotification, Long> {

    Optional<WorkflowRequestFinanceNotification> findByWorkflowRequestId(Long workflowRequestId);

    boolean existsByWorkflowRequestId(Long workflowRequestId);

    /**
     * Atomically claims a notification for sending. Returns 1 only for the single caller that
     * moved the row from the expected status to SENDING; any other concurrent dispatcher gets 0
     * and must not send.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkflowRequestFinanceNotification n " +
           "set n.status = :sending, n.attemptCount = n.attemptCount + 1, " +
           "n.lastAttemptAt = :now, n.version = n.version + 1 " +
           "where n.id = :id and n.status = :expected")
    int claimForSending(@Param("id") Long id,
                        @Param("expected") FinanceNotificationStatus expected,
                        @Param("sending") FinanceNotificationStatus sending,
                        @Param("now") LocalDateTime now);
}
