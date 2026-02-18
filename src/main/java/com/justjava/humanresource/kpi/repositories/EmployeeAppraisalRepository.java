package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeAppraisalRepository
        extends JpaRepository<EmployeeAppraisal, Long> {

    /* =====================================================
       DUPLICATE PREVENTION
       ===================================================== */

    boolean existsByEmployee_IdAndCycle_Id(
            Long employeeId,
            Long cycleId
    );

    Optional<EmployeeAppraisal> findByEmployee_IdAndCycle_Id(
            Long employeeId,
            Long cycleId
    );

    /* =====================================================
       REPORTING
       ===================================================== */

    List<EmployeeAppraisal> findByCycle_Id(Long cycleId);

    List<EmployeeAppraisal> findByEmployee_Id(Long employeeId);

    List<EmployeeAppraisal> findByEmployee_IdAndCompletedAtIsNotNull(
            Long employeeId
    );

    List<EmployeeAppraisal> findByCycle_IdAndCompletedAtIsNotNull(
            Long cycleId
    );
    @Query("""
       SELECT AVG(a.finalScore)
       FROM EmployeeAppraisal a
       WHERE a.employee.department.id = :departmentId
       AND a.completedAt IS NOT NULL
       """)
    Double averageFinalScoreByDepartment(
            @Param("departmentId") Long departmentId
    );

}
