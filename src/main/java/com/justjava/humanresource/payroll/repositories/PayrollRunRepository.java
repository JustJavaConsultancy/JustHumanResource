package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;
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
    findTopByEmployeeIdAndPayrollDateOrderByVersionNumberDesc(
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



}