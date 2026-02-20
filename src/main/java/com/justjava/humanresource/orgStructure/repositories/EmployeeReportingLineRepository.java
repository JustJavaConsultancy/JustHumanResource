package com.justjava.humanresource.orgStructure.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.orgStructure.entity.EmployeeReportingLine;
import com.justjava.humanresource.orgStructure.enums.ReportingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeReportingLineRepository
        extends JpaRepository<EmployeeReportingLine, Long> {

    /* =========================================================
       1️⃣ FIND EFFECTIVE MANAGER (PRIMARY LOOKUP)
       ========================================================= */

    @Query("""
        SELECT r FROM EmployeeReportingLine r
        WHERE r.employee.id = :employeeId
          AND r.reportingType = :type
          AND r.status = 'ACTIVE'
          AND :date BETWEEN r.effectiveFrom
          AND COALESCE(r.effectiveTo, :date)
    """)
    Optional<EmployeeReportingLine> findEffectiveManager(
            @Param("employeeId") Long employeeId,
            @Param("type") ReportingType type,
            @Param("date") LocalDate date
    );

    /* =========================================================
       2️⃣ FIND ACTIVE LINE (FOR SAFE REMOVAL)
       ========================================================= */

    @Query("""
        SELECT r FROM EmployeeReportingLine r
        WHERE r.employee.id = :employeeId
          AND r.reportingType = :type
          AND r.status = 'ACTIVE'
          AND r.effectiveTo IS NULL
    """)
    Optional<EmployeeReportingLine> findActiveLine(
            @Param("employeeId") Long employeeId,
            @Param("type") ReportingType type
    );

    /* =========================================================
       3️⃣ FIND DIRECT REPORTS (FOR TREE BUILDING)
       ========================================================= */

    @Query("""
        SELECT r FROM EmployeeReportingLine r
        WHERE r.manager.id = :managerId
          AND r.reportingType = :type
          AND r.status = 'ACTIVE'
          AND :date BETWEEN r.effectiveFrom
          AND COALESCE(r.effectiveTo, :date)
    """)
    List<EmployeeReportingLine> findDirectReports(
            @Param("managerId") Long managerId,
            @Param("type") ReportingType type,
            @Param("date") LocalDate date
    );

    /* =========================================================
       4️⃣ CHECK EXISTING EFFECTIVE LINE (PREVENT DUPLICATE PRIMARY)
       ========================================================= */

    @Query("""
        SELECT COUNT(r) FROM EmployeeReportingLine r
        WHERE r.employee.id = :employeeId
          AND r.reportingType = :type
          AND r.status = 'ACTIVE'
          AND r.effectiveTo IS NULL
    """)
    long countActiveLines(
            @Param("employeeId") Long employeeId,
            @Param("type") ReportingType type
    );

    /* =========================================================
       5️⃣ POSTGRESQL RECURSIVE MANAGER CHAIN (ENTERPRISE)
       ========================================================= */

    @Query(value = """
        WITH RECURSIVE manager_chain AS (
            SELECT er.manager_id
            FROM employee_reporting_lines er
            WHERE er.employee_id = :employeeId
              AND er.reporting_type = :type
              AND er.status = 'ACTIVE'
              AND :date BETWEEN er.effective_from
              AND COALESCE(er.effective_to, :date)

            UNION

            SELECT er2.manager_id
            FROM employee_reporting_lines er2
            INNER JOIN manager_chain mc
                ON er2.employee_id = mc.manager_id
            WHERE er2.reporting_type = :type
              AND er2.status = 'ACTIVE'
              AND :date BETWEEN er2.effective_from
              AND COALESCE(er2.effective_to, :date)
        )
        SELECT manager_id FROM manager_chain
    """, nativeQuery = true)
    List<Long> findManagerChainIds(
            @Param("employeeId") Long employeeId,
            @Param("type") String type,
            @Param("date") LocalDate date
    );

    /* =========================================================
       6️⃣ CHECK CIRCULAR REPORTING (CRITICAL SAFETY)
       ========================================================= */

    @Query(value = """
        WITH RECURSIVE hierarchy AS (
            SELECT er.employee_id, er.manager_id
            FROM employee_reporting_lines er
            WHERE er.employee_id = :managerId
              AND er.status = 'ACTIVE'

            UNION

            SELECT er2.employee_id, er2.manager_id
            FROM employee_reporting_lines er2
            INNER JOIN hierarchy h
                ON er2.employee_id = h.manager_id
            WHERE er2.status = 'ACTIVE'
        )
        SELECT COUNT(*) > 0
        FROM hierarchy
        WHERE manager_id = :employeeId
    """, nativeQuery = true)
    boolean wouldCreateCycle(
            @Param("employeeId") Long employeeId,
            @Param("managerId") Long managerId
    );
}
