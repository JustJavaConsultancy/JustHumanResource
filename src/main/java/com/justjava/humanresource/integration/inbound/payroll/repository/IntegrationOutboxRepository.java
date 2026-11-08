package com.justjava.humanresource.integration.inbound.payroll.repository;

import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationOutboxEvent;
import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;




public interface IntegrationOutboxRepository extends JpaRepository<IntegrationOutboxEvent, UUID> {
    Optional<IntegrationOutboxEvent> findFirstByAggregateIdOrderByCreatedAtDesc(String aggregateId);
    @Query(value = """
            SELECT * FROM integration_outbox
            WHERE (status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
               OR (status = 'PUBLISHING' AND lease_until <= :now)
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<IntegrationOutboxEvent> findDueForClaim(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize);

    @Modifying
    @Query("""
            UPDATE IntegrationOutboxEvent e SET e.status = :status, e.publishedAt = :publishedAt,
                e.leaseUntil = null, e.lastError = null
            WHERE e.eventId = :eventId AND e.status = com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus.PUBLISHING
              AND e.leaseUntil = :leaseUntil
            """)
    int markPublished(
            @Param("eventId") UUID eventId,
            @Param("status") OutboxStatus status,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying
    @Query("""
            UPDATE IntegrationOutboxEvent e SET e.status = :status, e.nextAttemptAt = :nextAttemptAt,
                e.lastError = :lastError, e.leaseUntil = null
            WHERE e.eventId = :eventId AND e.status = com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus.PUBLISHING
              AND e.leaseUntil = :leaseUntil
            """)
    int returnToPendingOrFail(
            @Param("eventId") UUID eventId,
            @Param("status") OutboxStatus status,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying
    @Query("""
            UPDATE IntegrationOutboxEvent e SET e.status = com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus.CANCELLED,
                e.leaseUntil = null
            WHERE e.eventId = :eventId AND e.status = com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus.PENDING
            """)
    int cancelPending(@Param("eventId") UUID eventId);
}
