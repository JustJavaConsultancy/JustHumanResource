package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.dto.PayrollRunDTO;
import com.justjava.humanresource.payroll.entity.PayrollRun;
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

    long countByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatusNot(
            Long companyId,
            LocalDate start,
            LocalDate end,
            PayrollRunStatus status
    );

    /* ============================================================
       COMPANY-SCOPED FETCH
       ============================================================ */

    List<PayrollRun>
    findByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatus(
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
          AND p.payrollDate BETWEEN :start AND :end
          AND p.status = :status
    """)
    BigDecimal sumGrossByCompanyAndPeriodAndStatus(
            @Param("companyId") Long companyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") PayrollRunStatus status
    );

    @Query("""
        SELECT COALESCE(SUM(p.totalDeductions), 0)
        FROM PayrollRun p
        WHERE p.employee.department.company.id = :companyId
          AND p.payrollDate BETWEEN :start AND :end
          AND p.status = :status
    """)
    BigDecimal sumDeductionsByCompanyAndPeriodAndStatus(
            @Param("companyId") Long companyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") PayrollRunStatus status
    );

    @Query("""
        SELECT COALESCE(SUM(p.netPay), 0)
        FROM PayrollRun p
        WHERE p.employee.department.company.id = :companyId
          AND p.payrollDate BETWEEN :start AND :end
          AND p.status = :status
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
}