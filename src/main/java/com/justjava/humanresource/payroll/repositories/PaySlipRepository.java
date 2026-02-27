package com.justjava.humanresource.payroll.repositories;


import com.justjava.humanresource.payroll.entity.PaySlip;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaySlipRepository extends JpaRepository<PaySlip, Long> {

    /* ============================================================
       IDEMPOTENCY CHECK
       ============================================================ */

    boolean existsByPayrollRunIdAndVersionNumber(
            Long payrollRunId,
            Integer versionNumber
    );

    /* ============================================================
       LATEST PAYSLIP FOR EMPLOYEE FOR A SPECIFIC PERIOD
       (21–20 SAFE via payrollRun.periodStart/End)
       ============================================================ */

    @Query("""
        SELECT ps
        FROM PaySlip ps
        WHERE ps.employee.id = :employeeId
          AND ps.payrollRun.periodStart = :periodStart
          AND ps.payrollRun.periodEnd = :periodEnd
          AND ps.versionNumber = (
               SELECT MAX(p2.versionNumber)
               FROM PaySlip p2
               WHERE p2.employee.id = :employeeId
                 AND p2.payrollRun.periodStart = :periodStart
                 AND p2.payrollRun.periodEnd = :periodEnd
          )
    """)
    Optional<PaySlip> findLatestByEmployeeAndPeriod(
            @Param("employeeId") Long employeeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    /* ============================================================
       LATEST PAYSLIPS FOR ALL EMPLOYEES FOR PERIOD (COMPANY SAFE)
       ============================================================ */

    @Query("""
        SELECT ps
        FROM PaySlip ps
        WHERE ps.employee.department.company.id = :companyId
          AND ps.payrollRun.periodStart = :periodStart
          AND ps.payrollRun.periodEnd = :periodEnd
          AND ps.versionNumber = (
               SELECT MAX(p2.versionNumber)
               FROM PaySlip p2
               WHERE p2.employee.id = ps.employee.id
                 AND p2.payrollRun.periodStart = :periodStart
                 AND p2.payrollRun.periodEnd = :periodEnd
          )
    """)
    List<PaySlip> findLatestForCompanyAndPeriod(
            @Param("companyId") Long companyId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    /* ============================================================
       LATEST PAYSLIPS FOR ALL CLOSED PERIODS (COMPANY SCOPED)
       ============================================================ */

    @Query("""
        SELECT ps
        FROM PaySlip ps
        WHERE ps.employee.department.company.id = :companyId
          AND ps.payrollRun.periodStart IN (
                SELECT p.periodStart
                FROM PayrollPeriod p
                WHERE p.companyId = :companyId
                  AND p.status = :status
          )
          AND ps.versionNumber = (
                SELECT MAX(p2.versionNumber)
                FROM PaySlip p2
                WHERE p2.employee.id = ps.employee.id
                  AND p2.payrollRun.periodStart = ps.payrollRun.periodStart
          )
    """)
    List<PaySlip> findLatestForCompanyByPeriodStatus(
            @Param("companyId") Long companyId,
            @Param("status") PayrollPeriodStatus status
    );

    /* ============================================================
       ALL LATEST PAYSLIPS FOR EMPLOYEE (HISTORY SAFE)
       ============================================================ */

    @Query("""
        SELECT ps
        FROM PaySlip ps
        WHERE ps.employee.id = :employeeId
          AND ps.versionNumber = (
               SELECT MAX(p2.versionNumber)
               FROM PaySlip p2
               WHERE p2.employee.id = ps.employee.id
                 AND p2.payrollRun.periodStart = ps.payrollRun.periodStart
          )
        ORDER BY ps.payrollRun.periodStart DESC
    """)
    List<PaySlip> findLatestByEmployee(
            @Param("employeeId") Long employeeId
    );

    /* ============================================================
       FINANCIAL AGGREGATION (LATEST ONLY, DEPARTMENT SAFE)
       ============================================================ */

    @Query("""
        SELECT COALESCE(SUM(ps.grossPay), 0)
        FROM PaySlip ps
        WHERE ps.employee.department.id = :departmentId
          AND ps.versionNumber = (
               SELECT MAX(p2.versionNumber)
               FROM PaySlip p2
               WHERE p2.employee.id = ps.employee.id
                 AND p2.payrollRun.periodStart = ps.payrollRun.periodStart
          )
    """)
    BigDecimal sumLatestGrossByDepartment(
            @Param("departmentId") Long departmentId
    );
}