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

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    /* ============================================================
       DUPLICATE PREVENTION
       ============================================================ */

    Optional<PayrollRun> findByEmployeeIdAndPayrollDate(
            Long employeeId,
            LocalDate payrollDate
    );

    /* ============================================================
       PERIOD VALIDATION (CLOSE CHECK)
       ============================================================ */

    long countByPayrollDateBetweenAndStatusNot(
            LocalDate start,
            LocalDate end,
            PayrollRunStatus status
    );

    /* ============================================================
       JOURNAL GENERATION SUPPORT
       ============================================================ */

    List<PayrollRun> findByPayrollDateBetweenAndStatus(
            LocalDate start,
            LocalDate end,
            PayrollRunStatus status
    );

    /* ============================================================
       RECONCILIATION SUPPORT
       ============================================================ */

    List<PayrollRun> findByPayrollDateBetween(
            LocalDate start,
            LocalDate end
    );

    /* ============================================================
       FINANCIAL AGGREGATION (ENTERPRISE OPTIMIZED)
       Avoids loading full entity list for large payrolls
       ============================================================ */

    @Query("""
        SELECT COALESCE(SUM(p.grossPay), 0)
        FROM PayrollRun p
        WHERE p.payrollDate BETWEEN :start AND :end
          AND p.status = :status
    """)
    BigDecimal sumGrossByPeriodAndStatus(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") PayrollRunStatus status
    );

    @Query("""
        SELECT COALESCE(SUM(p.totalDeductions), 0)
        FROM PayrollRun p
        WHERE p.payrollDate BETWEEN :start AND :end
          AND p.status = :status
    """)
    BigDecimal sumDeductionsByPeriodAndStatus(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") PayrollRunStatus status
    );

    @Query("""
        SELECT COALESCE(SUM(p.netPay), 0)
        FROM PayrollRun p
        WHERE p.payrollDate BETWEEN :start AND :end
          AND p.status = :status
    """)
    BigDecimal sumNetByPeriodAndStatus(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") PayrollRunStatus status
    );

    /* ============================================================
       EMPLOYEE COUNT (RECONCILIATION GATE)
       ============================================================ */

    long countByPayrollDateBetweenAndStatus(
            LocalDate start,
            LocalDate end,
            PayrollRunStatus status
    );
    Optional<PayrollRun> findTopByEmployeeIdAndPayrollDateOrderByVersionNumberDesc(
            Long employeeId,
            LocalDate payrollDate
    );
}
