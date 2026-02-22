package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.EmployeeBankDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface EmployeeBankDetailRepository
        extends JpaRepository<EmployeeBankDetail, Long> {

    @Query("""
        SELECT b FROM EmployeeBankDetail b
        WHERE b.employee.id = :employeeId
          AND b.primaryAccount = true
          AND b.status = 'ACTIVE'
          AND :date BETWEEN b.effectiveFrom
          AND COALESCE(b.effectiveTo, :date)
    """)
    Optional<EmployeeBankDetail> findPrimaryBankDetail(
            Long employeeId,
            LocalDate date
    );
}