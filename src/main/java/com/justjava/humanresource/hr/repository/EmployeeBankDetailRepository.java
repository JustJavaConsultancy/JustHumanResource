package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.EmployeeBankDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface EmployeeBankDetailRepository extends JpaRepository<EmployeeBankDetail, Long> {

    // Find the currently active primary bank detail for an employee
    @Query("SELECT b FROM EmployeeBankDetail b WHERE b.employee.id = :employeeId AND b.status = 'ACTIVE' AND (b.primaryAccount = true OR b.effectiveTo IS NULL) ORDER BY b.primaryAccount DESC, b.effectiveFrom DESC")
    Optional<EmployeeBankDetail> findActiveByEmployeeId(@Param("employeeId") Long employeeId);

    // Deactivate all active bank details for an employee (used before inserting a new one)
    @Modifying
    @Transactional
    @Query("UPDATE EmployeeBankDetail b SET b.status = 'INACTIVE', b.effectiveTo = CURRENT_DATE WHERE b.employee.id = :employeeId AND b.status = 'ACTIVE'")
    void deactivateAllByEmployeeId(@Param("employeeId") Long employeeId);
}