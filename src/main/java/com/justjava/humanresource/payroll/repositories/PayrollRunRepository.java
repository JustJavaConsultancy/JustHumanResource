package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.dto.PayrollRunDTO;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.report.dto.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository
        extends JpaRepository<PayrollRun, Long> {

    /* ============================================================
       DUPLICATE PREVENTION
       ============================================================ */

    Optional<PayrollRun>
    findTopByEmployeeIdAndPeriodEndOrderByVersionNumberDesc(
            Long employeeId,
            LocalDate payrollDate
    );

    /* ============================================================
       COMPANY-SCOPED PERIOD VALIDATION
       ============================================================ */

    long countByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatus(
            Long companyId,
            LocalDate start,
            LocalDate end,
            PayrollRunStatus status
    );

    @Query("""
    SELECT COUNT(p)
    FROM PayrollRun p
    WHERE p.employee.department.company.id = :companyId
      AND p.payrollDate BETWEEN :start AND :end
      AND p.versionNumber = (
          SELECT MAX(p2.versionNumber)
          FROM PayrollRun p2
          WHERE p2.employee.id = p.employee.id
          AND p2.payrollDate = p.payrollDate
      )
      AND p.status <> :status
""")
    long countLatestByCompanyAndPayrollDateBetweenAndStatusNot(
            @Param("companyId") Long companyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") PayrollRunStatus status
    );
    /* ============================================================
       COMPANY-SCOPED FETCH
       ============================================================ */

    @Query("""
    SELECT p
    FROM PayrollRun p
    WHERE p.employee.department.company.id = :companyId
      AND p.periodStart = :start
      AND p.periodEnd = :end
      AND p.status = :status
      AND p.versionNumber = (
          SELECT MAX(p2.versionNumber)
          FROM PayrollRun p2
          WHERE p2.employee.id = p.employee.id
          AND p2.periodStart = p.periodStart
          AND p2.periodEnd = p.periodEnd
      )
""")
    List<PayrollRun> findLatestByCompanyAndPeriodAndStatus(
            Long companyId,
            LocalDate start,
            LocalDate end,
            PayrollRunStatus status
    );
    List<PayrollRun>
    findByEmployee_Department_Company_IdAndPayrollDateBetween(
            Long companyId,
            LocalDate start,
            LocalDate end
    );

    /* ============================================================
       FINANCIAL AGGREGATION (COMPANY SAFE)
       ============================================================ */

    @Query("""
    SELECT COALESCE(SUM(p.grossPay), 0)
    FROM PayrollRun p
    WHERE p.employee.department.company.id = :companyId
      AND p.periodStart = :start
      AND p.periodEnd = :end
      AND p.status = :status
      AND p.versionNumber = (
          SELECT MAX(p2.versionNumber)
          FROM PayrollRun p2
          WHERE p2.employee.id = p.employee.id
          AND p2.periodStart = p.periodStart
          AND p2.periodEnd = p.periodEnd
      )
""")
    BigDecimal sumGrossByCompanyAndPeriodAndStatus(
            Long companyId,
            LocalDate start,
            LocalDate end,
            PayrollRunStatus status
    );
    @Query("""
    SELECT COALESCE(SUM(p.totalDeductions), 0)
    FROM PayrollRun p
    WHERE p.employee.department.company.id = :companyId
      AND p.periodStart = :start
      AND p.periodEnd = :end
      AND p.status = :status
      AND p.versionNumber = (
          SELECT MAX(p2.versionNumber)
          FROM PayrollRun p2
          WHERE p2.employee.id = p.employee.id
          AND p2.periodStart = p.periodStart
          AND p2.periodEnd = p.periodEnd
      )
""")
    BigDecimal sumDeductionsByCompanyAndPeriodAndStatus(
            Long companyId,
            LocalDate start,
            LocalDate end,
            PayrollRunStatus status
    );
    @Query("""
    SELECT COALESCE(SUM(p.netPay), 0)
    FROM PayrollRun p
    WHERE p.employee.department.company.id = :companyId
      AND p.payrollDate BETWEEN :start AND :end
      AND p.status = :status
      AND p.versionNumber = (
          SELECT MAX(p2.versionNumber)
          FROM PayrollRun p2
          WHERE p2.employee.id = p.employee.id
          AND p2.payrollDate = p.payrollDate
      )
""")
    BigDecimal sumNetByCompanyAndPeriodAndStatus(
            @Param("companyId") Long companyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") PayrollRunStatus status
    );
    @Query("""
    SELECT COUNT(p)
    FROM PayrollRun p
    WHERE p.employee.department.company.id = :companyId
      AND p.payrollDate BETWEEN :start AND :end
      AND p.status <> :status
""")
    long countByCompanyIdAndPayrollDateBetweenAndStatusNot(
            @Param("companyId") Long companyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") PayrollRunStatus status
    );
    @Query("""
    SELECT COUNT(p)
    FROM PayrollRun p
    WHERE p.employee.department.company.id = :companyId
      AND p.payrollDate BETWEEN :start AND :end
      AND p.status = :status
""")
    long countByCompanyIdAndPayrollDateBetweenAndStatus(
            @Param("companyId") Long companyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") PayrollRunStatus status
    );

    @Modifying
    @Query(value = """
INSERT INTO payroll_runs (
    employee_id,
    payroll_date,
    period_start,
    period_end,
    status,
    flowable_process_instance_id,
    payroll_year,
    run_type,
    version_number,
    gross_pay,
    non_gross_earnings,
    gross_difference,
    total_deductions,
    net_pay,
    parent_run_id,
    created_at,
    updated_at
)
SELECT
    pr.employee_id,
    :newPeriodEnd,
    :newPeriodStart,
    :newPeriodEnd,
    'POSTED',
    pr.flowable_process_instance_id,
    pr.payroll_year,
    'ORIGINAL',
    1,
    pr.gross_pay,
    pr.non_gross_earnings,
    pr.gross_difference,
    pr.total_deductions,
    pr.net_pay,
    pr.id,
    NOW(),
    NOW()
FROM payroll_runs pr
JOIN employees e ON e.id = pr.employee_id
JOIN departments d ON d.id = e.department_id
WHERE d.company_id = :companyId
AND pr.period_start = :oldPeriodStart
AND pr.period_end = :oldPeriodEnd
AND pr.status = 'POSTED'
AND pr.version_number = (
    SELECT MAX(pr2.version_number)
    FROM payroll_runs pr2
    WHERE pr2.employee_id = pr.employee_id
    AND pr2.period_start = pr.period_start
    AND pr2.period_end = pr.period_end
)
""", nativeQuery = true)
    void bulkCarryForward(
            @Param("companyId") Long companyId,
            @Param("oldPeriodStart") LocalDate oldPeriodStart,
            @Param("oldPeriodEnd") LocalDate oldPeriodEnd,
            @Param("newPeriodStart") LocalDate newPeriodStart,
            @Param("newPeriodEnd") LocalDate newPeriodEnd
    );
    long countByEmployee_Department_Company_IdAndPayrollDateBetween(
            Long companyId,
            LocalDate start,
            LocalDate end
    );
    Optional<PayrollRun>
    findTopByEmployee_IdAndPayrollYearAndStatusOrderByPayrollDateDesc(
            Long employeeId,
            Integer payrollYear,
            PayrollRunStatus status
    );
    @Query("""
SELECT new com.justjava.humanresource.payroll.dto.PayrollRunDTO(
    pr.id,
    e.id,
    e.employeeNumber,
    CONCAT(e.firstName,' ',e.lastName),
    pr.payrollDate,
    pr.periodStart,
    pr.periodEnd,
    pr.grossPay,
    pr.totalDeductions,
    pr.netPay
)
FROM PayrollRun pr
JOIN pr.employee e
WHERE e.department.company.id = :companyId
AND pr.periodStart = :periodStart
AND pr.periodEnd = :periodEnd
AND pr.versionNumber = (
        SELECT MAX(pr2.versionNumber)
        FROM PayrollRun pr2
        WHERE pr2.employee.id = pr.employee.id
        AND pr2.payrollDate = pr.payrollDate
)
""")
    List<PayrollRunDTO> findLatestPayrollRunsForPeriod(
            Long companyId,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    @Query("""
SELECT new com.justjava.humanresource.payroll.report.dto.PayrollSummaryDTO(
    d.name,
    SUM(pr.grossPay),
    SUM(pr.totalDeductions),
    SUM(pr.netPay),
    SUM(CASE WHEN li.componentCode = 'PAYE' THEN li.amount END),
    SUM(CASE WHEN li.componentCode = 'PENSION' THEN li.amount END)
)
FROM PayrollRun pr
JOIN pr.employee e
JOIN e.department d
LEFT JOIN PayrollLineItem li ON li.payrollRun.id = pr.id
WHERE d.company.id = :companyId
AND pr.periodStart >= :start
AND pr.periodEnd <= :end
AND pr.status = com.justjava.humanresource.core.enums.PayrollRunStatus.POSTED
GROUP BY d.name
""")
    List<PayrollSummaryDTO> getPayrollSummary(
            Long companyId,
            LocalDate start,
            LocalDate end
    );
    @Query("""
SELECT new com.justjava.humanresource.payroll.report.dto.ComponentBreakdownDTO(
    li.componentCode,
    li.description,
    SUM(li.amount)
)
FROM PayrollLineItem li
JOIN li.payrollRun pr
JOIN pr.employee e
WHERE e.department.company.id = :companyId
AND pr.periodStart >= :start
AND pr.periodEnd <= :end
AND li.componentType = com.justjava.humanresource.payroll.enums.PayComponentType.EARNING
GROUP BY li.componentCode, li.description
""")
    List<ComponentBreakdownDTO> getEarningsBreakdown(
            Long companyId,
            LocalDate start,
            LocalDate end
    );
    @Query(value = """
SELECT 
    TO_CHAR(pr.payroll_date, 'YYYY-MM') AS period,
    li.component_code,
    SUM(li.amount) AS total_amount
FROM payroll_line_items li
JOIN payroll_runs pr ON pr.id = li.payroll_run_id
JOIN employees e ON e.id = pr.employee_id
JOIN departments d ON d.id = e.department_id
WHERE d.company_id = :companyId
GROUP BY 
    TO_CHAR(pr.payroll_date, 'YYYY-MM'),
    li.component_code
ORDER BY 
    period
""", nativeQuery = true)
    List<Object[]> getComponentTrendRaw(Long companyId);

    @Query("""
SELECT new com.justjava.humanresource.payroll.report.dto.PensionReportDTO(
    e.id,
    CONCAT(e.firstName,' ',e.lastName),
    SUM(CASE WHEN li.componentCode = 'PENSION_EMP' THEN li.amount END),
    SUM(CASE WHEN li.componentCode = 'PENSION_EMPLOYER' THEN li.amount END),
    pr.appliedPensionSchemeName
)
FROM PayrollLineItem li
JOIN li.payrollRun pr
JOIN pr.employee e
WHERE e.department.company.id = :companyId
AND pr.periodStart >= :start
AND pr.periodEnd <= :end
GROUP BY e.id, e.firstName, e.lastName, pr.appliedPensionSchemeName
""")
    List<PensionReportDTO> getPensionReport(
            Long companyId,
            LocalDate start,
            LocalDate end
    );
    @Query("""
SELECT new com.justjava.humanresource.payroll.report.dto.ComponentBreakdownDTO(
    li.componentCode,
    li.description,
    SUM(li.amount)
)
FROM PayrollLineItem li
JOIN li.payrollRun pr
JOIN pr.employee e
WHERE e.department.company.id = :companyId
AND pr.periodStart >= :start
AND pr.periodEnd <= :end
AND li.componentType = com.justjava.humanresource.payroll.enums.PayComponentType.DEDUCTION
GROUP BY li.componentCode, li.description
""")
    List<ComponentBreakdownDTO> getDeductionBreakdown(
            Long companyId,
            LocalDate start,
            LocalDate end
    );
    @Query("""
SELECT new com.justjava.humanresource.payroll.report.dto.PayeReportDTO(
    e.id,
    CONCAT(e.firstName,' ',e.lastName),
    SUM(CASE WHEN li.taxable = true THEN li.amount END),
    SUM(CASE WHEN li.componentCode = 'PAYE' THEN li.amount END),
    MAX(pr.ytdPaye)
)
FROM PayrollLineItem li
JOIN li.payrollRun pr
JOIN pr.employee e
WHERE e.department.company.id = :companyId
AND pr.periodStart >= :start
AND pr.periodEnd <= :end
GROUP BY e.id, e.firstName, e.lastName
""")
    List<PayeReportDTO> getPayeReport(
            Long companyId,
            LocalDate start,
            LocalDate end
    );
    Optional<PayrollRun>
    findTopByEmployee_IdAndPeriodStartAndPeriodEndOrderByVersionNumberDesc(
            Long employeeId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
    @Query("""
SELECT COALESCE(MAX(pr.versionNumber), 0)
FROM PayrollRun pr
WHERE pr.employee.id = :employeeId
AND pr.periodStart = :periodStart
AND pr.periodEnd = :periodEnd
""")
    Integer findMaxVersionForEmployeeAndPeriod(
            Long employeeId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
    @Query("""
SELECT pr
FROM PayrollRun pr
JOIN pr.employee e
WHERE e.department.company.id = :companyId
AND pr.periodStart = :periodStart
AND pr.periodEnd = :periodEnd
AND pr.versionNumber = (
    SELECT MAX(pr2.versionNumber)
    FROM PayrollRun pr2
    WHERE pr2.employee.id = pr.employee.id
    AND pr2.periodStart = pr.periodStart
    AND pr2.periodEnd = pr.periodEnd
)
""")
    List<PayrollRun> findLatestRunsPerEmployeeForPeriod(
            @Param("companyId") Long companyId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );
}