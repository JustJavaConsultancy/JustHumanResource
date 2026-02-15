package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.EmployeeDeduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeDeductionRepository
        extends JpaRepository<EmployeeDeduction, Long> {

    /* ============================================================
       BACKWARD COMPATIBILITY METHOD
       ============================================================ */

    List<EmployeeDeduction> findByEmployeeId(Long employeeId);

    /* ============================================================
       EFFECTIVE-DATED ACTIVE DEDUCTIONS
       ============================================================ */

    @Query("""
        SELECT ed FROM EmployeeDeduction ed
        WHERE ed.employee.id = :employeeId
          AND ed.status = :status
          AND ed.effectiveFrom <= :date
          AND (ed.effectiveTo IS NULL OR ed.effectiveTo >= :date)
    """)
    List<EmployeeDeduction> findActiveDeductions(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );
}
