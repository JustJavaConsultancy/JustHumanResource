package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.PayGroupDeduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PayGroupDeductionRepository
        extends JpaRepository<PayGroupDeduction, Long> {

    /* ============================================================
       EXISTING METHOD (Backward Compatibility)
       ============================================================ */

    List<PayGroupDeduction> findByPayGroupId(Long payGroupId);

    /* ============================================================
       EFFECTIVE-DATED ACTIVE DEDUCTIONS
       ============================================================ */

    @Query("""
        SELECT pgd FROM PayGroupDeduction pgd
        WHERE pgd.payGroup.id = :payGroupId
          AND pgd.status = :status
          AND pgd.effectiveFrom <= :date
          AND (pgd.effectiveTo IS NULL OR pgd.effectiveTo >= :date)
    """)
    List<PayGroupDeduction> findActiveDeductions(
            @Param("payGroupId") Long payGroupId,
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );
}
