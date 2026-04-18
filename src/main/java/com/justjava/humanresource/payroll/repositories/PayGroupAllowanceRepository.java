package com.justjava.humanresource.payroll.repositories;


import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.PayGroupAllowance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PayGroupAllowanceRepository
        extends JpaRepository<PayGroupAllowance, Long> {

    /* ============================================================
       EXISTING METHOD (UNCHANGED FOR BACKWARD COMPATIBILITY)
       ============================================================ */

    List<PayGroupAllowance> findByPayGroupId(Long payGroupId);

    /* ============================================================
       EFFECTIVE-DATED ACTIVE ALLOWANCES
       ============================================================ */

    @Query("""
        SELECT pga FROM PayGroupAllowance pga
        WHERE pga.payGroup.id = :payGroupId
          AND pga.status = :status
          AND pga.effectiveFrom <= :date
          AND (pga.effectiveTo IS NULL OR pga.effectiveTo >= :date)
    """)
    List<PayGroupAllowance> findActiveAllowances(
            @Param("payGroupId") Long payGroupId,
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );

    /* ============================================================
       ALL ASSIGNED ALLOWANCES (INCLUDING FUTURE EFFECTIVE DATES)
       For UI display purposes - shows all assignments regardless of date
       ============================================================ */

    @Query("""
        SELECT pga FROM PayGroupAllowance pga
        WHERE pga.payGroup.id = :payGroupId
          AND pga.status = :status
          AND (pga.effectiveTo IS NULL OR pga.effectiveTo >= :date)
    """)
    List<PayGroupAllowance> findAllAssignedAllowances(
            @Param("payGroupId") Long payGroupId,
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );
}
