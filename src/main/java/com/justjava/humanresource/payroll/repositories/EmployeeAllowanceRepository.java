package com.justjava.humanresource.payroll.repositories;


import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.EmployeeAllowance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeAllowanceRepository
        extends JpaRepository<EmployeeAllowance, Long> {

    /* ============================================================
       EXISTING METHOD (UNCHANGED FOR COMPATIBILITY)
       ============================================================ */

    List<EmployeeAllowance> findByEmployeeId(Long employeeId);

    /* ============================================================
       EFFECTIVE-DATED ACTIVE ALLOWANCES
       ============================================================ */

    @Query("""
        SELECT ea FROM EmployeeAllowance ea
        WHERE ea.employee.id = :employeeId
          AND ea.status = :status
          AND ea.effectiveFrom <= :date
          AND (ea.effectiveTo IS NULL OR ea.effectiveTo >= :date)
    """)
    List<EmployeeAllowance> findActiveAllowances(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );
}
