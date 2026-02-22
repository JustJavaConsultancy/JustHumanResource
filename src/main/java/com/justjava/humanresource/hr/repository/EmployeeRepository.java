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
    List<Employee> findEmployeesWithKpiMeasurementForPeriod(
            @Param("period") java.time.YearMonth period
    );
}
