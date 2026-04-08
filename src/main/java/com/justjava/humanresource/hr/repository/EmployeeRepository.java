package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee,Long> {

    Optional<Employee> findByEmployeeNumber(String employeeNumber);
    @Query("""
   SELECT COUNT(e)
   FROM Employee e
   WHERE e.employmentStatus = 'ACTIVE'
""")
    long countByEmploymentStatusActive();
    List<Employee> findByPayGroup_Id(Long payGroupId);
    @Query("""
       SELECT COUNT(e)
       FROM Employee e
       WHERE e.department.id = :departmentId
       """)
    Long countEmployeesByDepartment(@Param("departmentId") Long departmentId);
/* =========================================================
   EMPLOYEES WITH ANY KPI MEASUREMENT
   ========================================================= */

    @Query("""
       SELECT DISTINCT e
       FROM Employee e
       WHERE EXISTS (
            SELECT 1
            FROM KpiMeasurement m
            WHERE m.employee.id = e.id
       )
       """)
    Page<Employee> findEmployeesWithAnyKpiMeasurement(Pageable pageable);

/* =========================================================
   EMPLOYEES WITH KPI MEASUREMENT FOR A PERIOD
   (Recommended for Appraisal / Monthly Processing)
   ========================================================= */

    @Query("""
       SELECT DISTINCT e
       FROM Employee e
       WHERE EXISTS (
            SELECT 1
            FROM KpiMeasurement m
            WHERE m.employee.id = e.id
            AND m.period = :period
       )
       """)
    Page<Employee> findEmployeesWithKpiMeasurementForPeriod(
            @Param("period") java.time.YearMonth period,Pageable pageable
    );

    Optional<Employee> findByEmail(String email);

    @Query("SELECT e FROM Employee e " +
            "LEFT JOIN FETCH e.bankDetails " +
            "WHERE e.id = :id")
    Optional<Employee> findByIdWithBankDetails(@Param("id") Long id);
    @Query("""
       SELECT e.department.id, COUNT(e)
       FROM Employee e
       GROUP BY e.department.id
       """)
    List<Object[]> countEmployeesByDepartmentGroup();


    // CHECK EMPLOYEE BANK DETAILS
    @Query("""
    SELECT e FROM Employee e 
    LEFT JOIN e.bankDetails bd 
    WHERE e.department.company.id = :companyId 
      AND e.status IN (com.justjava.humanresource.core.enums.RecordStatus.ACTIVE, com.justjava.humanresource.core.enums.RecordStatus.INACTIVE)
      AND (bd IS NULL OR NOT EXISTS (
          SELECT b FROM EmployeeBankDetail b 
          WHERE b.employee = e 
          AND b.status = com.justjava.humanresource.core.enums.RecordStatus.ACTIVE
          AND b.accountNumber IS NOT NULL 
          AND b.accountNumber != ''
      ))
""")
    List<Employee> findEmployeesMissingBankDetails(@Param("companyId") Long companyId);
}
