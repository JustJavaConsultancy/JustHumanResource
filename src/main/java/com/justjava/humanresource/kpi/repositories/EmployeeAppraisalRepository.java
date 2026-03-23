package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import org.springframework.data.domain.Pageable;
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
    @Query("""
       SELECT a
       FROM EmployeeAppraisal a
       WHERE a.cycle.active = true
       AND a.completedAt IS NULL
       """)
    List<EmployeeAppraisal> findAllActiveAppraisals();
    @Query("""
       SELECT a
       FROM EmployeeAppraisal a
       JOIN FETCH a.employee e
       JOIN FETCH a.cycle c
       WHERE a.cycle.id = :cycleId
       """)
    List<EmployeeAppraisal> findByCycleWithDetails(
            @Param("cycleId") Long cycleId
    );
    @Query("""
       SELECT a.employee.id, a.employee.lastName, a.finalScore
       FROM EmployeeAppraisal a
       WHERE a.cycle.id = :cycleId
       AND a.finalScore IS NOT NULL
       ORDER BY a.finalScore DESC
       """)
    List<Object[]> findTopPerformersByAppraisal(
            @Param("cycleId") Long cycleId,
            Pageable pageable
    );
}
