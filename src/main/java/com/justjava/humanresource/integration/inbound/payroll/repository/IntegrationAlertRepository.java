package com.justjava.humanresource.integration.inbound.payroll.repository;

import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationAlert;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;




public interface IntegrationAlertRepository extends JpaRepository<IntegrationAlert, Long> {
    Optional<IntegrationAlert> findByDedupeKey(String dedupeKey);
    List<IntegrationAlert> findByPayrollPeriodIdAndStatusOrderByCreatedAtDesc(
            Long payrollPeriodId, IntegrationAlertStatus status);
    List<IntegrationAlert> findByPayrollPeriodIdInAndStatusOrderByCreatedAtDesc(
            Collection<Long> periodIds, IntegrationAlertStatus status);
    List<IntegrationAlert> findByStatusOrderByCreatedAtDesc(IntegrationAlertStatus status);

    @Modifying
    @Query(value = """
            INSERT INTO integration_alert
                (type, dedupe_key, journal_reference, payroll_period_id, message, status, created_at)
            VALUES (:type, :dedupeKey, :journalReference, :periodId, :message, 'OPEN', :createdAt)
            ON CONFLICT (dedupe_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("type") String type,
            @Param("dedupeKey") String dedupeKey,
            @Param("journalReference") String journalReference,
            @Param("periodId") Long periodId,
            @Param("message") String message,
            @Param("createdAt") LocalDateTime createdAt);

    @Modifying
    @Query("""
            UPDATE IntegrationAlert a SET a.status = com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertStatus.RESOLVED,
                a.resolvedAt = :resolvedAt
            WHERE a.dedupeKey = :dedupeKey
              AND a.status = com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertStatus.OPEN
            """)
    int resolveOpen(@Param("dedupeKey") String dedupeKey, @Param("resolvedAt") LocalDateTime resolvedAt);
}
