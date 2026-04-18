package com.justjava.humanresource.payroll.repositories;


import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.EmployeeAllowance;
import com.justjava.humanresource.payroll.entity.PayGroupAllowance;
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
    @Query("""
    SELECT ea FROM EmployeeAllowance ea
    WHERE ea.employee.id = :employeeId
      AND ea.status = com.justjava.humanresource.core.enums.RecordStatus.ACTIVE
      AND ea.effectiveFrom > :today
    ORDER BY ea.effectiveFrom ASC
""")
    List<EmployeeAllowance> findFutureAllowancesByEmployee(
            Long employeeId,
            LocalDate today
    );

    @Query("""
    SELECT pga FROM PayGroupAllowance pga
    WHERE pga.payGroup.id = :payGroupId
      AND pga.status = com.justjava.humanresource.core.enums.RecordStatus.ACTIVE
      AND pga.effectiveFrom > :today
    ORDER BY pga.effectiveFrom ASC
""")
    List<PayGroupAllowance> findFutureAllowancesByPayGroup(
            Long payGroupId,
            LocalDate today
    );
    @Query("""
    SELECT ea FROM EmployeeAllowance ea
    JOIN ea.employee e
    JOIN e.department d
    WHERE d.company.id = :companyId
      AND ea.status = com.justjava.humanresource.core.enums.RecordStatus.ACTIVE
      AND ea.effectiveFrom > :today
    ORDER BY ea.effectiveFrom ASC
""")
    List<EmployeeAllowance> findFutureAllowancesByCompany(
            Long companyId,
            LocalDate today
    );
}
