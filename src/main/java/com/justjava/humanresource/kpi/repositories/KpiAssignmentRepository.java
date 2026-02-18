package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KpiAssignmentRepository
        extends JpaRepository<KpiAssignment, Long> {

    /* =========================================================
       BASIC LOOKUPS
       ========================================================= */

    List<KpiAssignment> findByEmployee_Id(Long employeeId);

    List<KpiAssignment> findByJobStep_Id(Long jobStepId);

    List<KpiAssignment> findByEmployee_IdAndActiveTrue(Long employeeId);

    List<KpiAssignment> findByJobStep_IdAndActiveTrue(Long jobStepId);

    /* =========================================================
       VALIDITY-AWARE LOOKUP
       Used for appraisal calculation
       ========================================================= */

    @Query("""
           SELECT a
           FROM KpiAssignment a
           WHERE a.active = true
           AND (
                (a.employee.id = :employeeId)
           )
           AND (a.validFrom IS NULL OR a.validFrom <= :referenceDate)
           AND (a.validTo IS NULL OR a.validTo >= :referenceDate)
           """)
    List<KpiAssignment> findEffectiveAssignmentsForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("referenceDate") LocalDate referenceDate
    );
    @Query("""
           SELECT a
           FROM KpiAssignment a
           WHERE a.active = true
           AND (
                (a.employee.jobStep.id = :jobStepId)
           )
           AND (a.validFrom IS NULL OR a.validFrom <= :referenceDate)
           AND (a.validTo IS NULL OR a.validTo >= :referenceDate)
           """)
    List<KpiAssignment> findEffectiveAssignmentsForJobStep(
            @Param("employeeId") Long jobStepId,
            @Param("referenceDate") LocalDate referenceDate
    );

    /* =========================================================
       SIMPLE EXISTENCE CHECK (used by delegate)
       ========================================================= */

    boolean existsByEmployee_IdAndKpi_IdAndActiveTrue(
            Long employeeId,
            Long kpiId
    );

    boolean existsByJobStep_IdAndKpi_IdAndActiveTrue(
            Long jobStepId,
            Long kpiId
    );

    /* =========================================================
       CROSS-SCOPE EXISTENCE CHECK
       (Used during measurement validation)
       ========================================================= */

    @Query("""
           SELECT COUNT(a) > 0
           FROM KpiAssignment a
           WHERE a.active = true
           AND a.kpi.id = :kpiId
           AND (
                (a.employee.id = :employeeId)
                OR
                (a.employee IS NULL AND a.jobStep.id = :jobStepId)
           )
           """)
    boolean existsEffectiveAssignment(
            @Param("employeeId") Long employeeId,
            @Param("jobStepId") Long jobStepId,
            @Param("kpiId") Long kpiId
    );

    /* =========================================================
       DUPLICATE PREVENTION FETCH
       ========================================================= */

    Optional<KpiAssignment> findByEmployee_IdAndKpi_IdAndActiveTrue(
            Long employeeId,
            Long kpiId
    );

    Optional<KpiAssignment> findByJobStep_IdAndKpi_IdAndActiveTrue(
            Long jobStepId,
            Long kpiId
    );

    /* =========================================================
       WEIGHT AGGREGATION
       Important for validation before saving new assignments
       ========================================================= */

    @Query("""
           SELECT COALESCE(SUM(a.weight), 0)
           FROM KpiAssignment a
           WHERE a.active = true
           AND (
                (a.employee.id = :employeeId)
                OR
                (a.employee IS NULL AND a.jobStep.id = :jobStepId)
           )
           """)
    BigDecimal sumActiveWeights(
            @Param("employeeId") Long employeeId,
            @Param("jobStepId") Long jobStepId
    );

    /* =========================================================
       DEACTIVATION HELPERS
       Used during role change
       ========================================================= */

    @Query("""
           SELECT a
           FROM KpiAssignment a
           WHERE a.employee.id = :employeeId
           AND a.active = true
           """)
    List<KpiAssignment> findActiveEmployeeAssignments(
            @Param("employeeId") Long employeeId
    );
    @Query("""
       SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
       FROM KpiAssignment a
       WHERE a.active = true
       AND a.kpi.id = :kpiId
       AND (
            (a.employee.id = :employeeId)
            OR
            (a.employee IS NULL AND a.jobStep.id = :jobStepId)
       )
       AND (a.validFrom IS NULL OR a.validFrom <= CURRENT_DATE)
       AND (a.validTo IS NULL OR a.validTo >= CURRENT_DATE)
       """)
    boolean existsActiveAssignment(
            @Param("employeeId") Long employeeId,
            @Param("jobStepId") Long jobStepId,
            @Param("kpiId") Long kpiId
    );

}
