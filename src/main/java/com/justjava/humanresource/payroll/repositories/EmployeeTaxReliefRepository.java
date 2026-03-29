package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.payroll.entity.EmployeeTaxRelief;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Repository
public interface EmployeeTaxReliefRepository
        extends JpaRepository<EmployeeTaxRelief, Long> {

    @Query("""
        SELECT e
        FROM EmployeeTaxRelief e
        WHERE e.employeeId = :employeeId
          AND e.status = :status
          AND (e.effectiveFrom IS NULL OR e.effectiveFrom <= :date)
          AND (e.effectiveTo IS NULL OR e.effectiveTo >= :date)
    """)
    List<EmployeeTaxRelief> findActiveReliefs(
            Long employeeId,
            LocalDate date,
            RecordStatus status
    );

    List<EmployeeTaxRelief> findByEmployeeId(Long employeeId);
}