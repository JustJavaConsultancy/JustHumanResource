package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeePositionHistoryRepository
        extends JpaRepository<EmployeePositionHistory, Long> {

    /* ============================================================
       🎯 CANONICAL: CURRENT POSITION (USE THIS EVERYWHERE)
       ============================================================ */

    @Query("""
        SELECT eph FROM EmployeePositionHistory eph
        WHERE eph.employee.id = :employeeId
          AND eph.current = true
    """)
    List<EmployeePositionHistory> findCurrentPositionsInternal(
            @Param("employeeId") Long employeeId
    );

    default Optional<EmployeePositionHistory> findCurrentPosition(Long employeeId) {
        List<EmployeePositionHistory> list = findCurrentPositionsInternal(employeeId);
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(0));
    }

    /* ============================================================
       📅 DATE-BASED RESOLUTION (HISTORICAL LOOKUP)
       ============================================================ */

    @Query("""
        SELECT eph FROM EmployeePositionHistory eph
        WHERE eph.employee.id = :employeeId
          AND eph.status = :status
          AND eph.effectiveFrom <= :date
          AND (eph.effectiveTo IS NULL OR eph.effectiveTo >= :date)
        ORDER BY eph.effectiveFrom DESC
    """)
    List<EmployeePositionHistory> findEffectivePosition(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );

    default Optional<EmployeePositionHistory> resolvePosition(
            Long employeeId,
            LocalDate date,
            RecordStatus status
    ) {
        List<EmployeePositionHistory> list = findEffectivePosition(employeeId, date, status);
        if (list.isEmpty()) return Optional.empty();
        if (list.size() > 1) {
            throw new IllegalStateException(
                    "Multiple active positions found for employee " + employeeId + " on " + date
            );
        }
        return Optional.of(list.get(0));
    }

    /* ============================================================
       👥 PAYGROUP-BASED LOOKUP
       ============================================================ */

    @Query("""
        SELECT eph FROM EmployeePositionHistory eph
        WHERE eph.payGroup.id = :payGroupId
          AND eph.status = :status
          AND eph.effectiveFrom <= :date
          AND (eph.effectiveTo IS NULL OR eph.effectiveTo >= :date)
    """)
    List<EmployeePositionHistory> findEmployeesByPayGroupAndDate(
            @Param("payGroupId") Long payGroupId,
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );

    /* ============================================================
       ⚡ LEGACY SUPPORT (SAFE TO KEEP)
       ============================================================ */

    boolean existsByEmployee_IdAndCurrentTrue(Long employeeId);

    Optional<EmployeePositionHistory> findByEmployee_IdAndCurrentTrue(Long employeeId);

    List<EmployeePositionHistory> findAllByEmployee_IdAndCurrentTrue(Long employeeId);

    List<EmployeePositionHistory> findByCurrentTrueAndEffectiveToIsNull();

    long countByCurrentTrue();

    /* ============================================================
       📊 VARIANCE REPORT — position changes within a period
       ============================================================ */

    /**
     * Returns all position history records for an employee whose
     * {@code effectiveFrom} falls within [{@code start}, {@code end}].
     * Used by the Payroll Variance Report to detect salary/promotion changes.
     */
    List<EmployeePositionHistory> findByEmployee_IdAndEffectiveFromBetween(
            Long employeeId,
            LocalDate start,
            LocalDate end
    );

    /* ============================================================
       🛡️ DUPLICATE GUARD — finds any existing record for employee + date
          regardless of current flag, to prevent constraint violations
       ============================================================ */

    @Query("""
        SELECT eph FROM EmployeePositionHistory eph
        WHERE eph.employee.id = :employeeId
          AND eph.effectiveFrom = :effectiveFrom
    """)
    List<EmployeePositionHistory> findAllByEmployeeIdAndEffectiveFrom(
            @Param("employeeId") Long employeeId,
            @Param("effectiveFrom") LocalDate effectiveFrom
    );
}