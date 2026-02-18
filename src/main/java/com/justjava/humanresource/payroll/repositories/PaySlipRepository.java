package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PaySlip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaySlipRepository
        extends JpaRepository<PaySlip, Long> {

    List<PaySlip> findByEmployee_Id(Long employeeId);

    List<PaySlip> findByPayDateBetween(LocalDate start, LocalDate end);

    List<PaySlip> findByEmployee_IdAndPayDateBetween(
            Long employeeId,
            LocalDate start,
            LocalDate end
    );
    /* ============================================================
   LATEST PAYSLIP FOR EMPLOYEE FOR PERIOD
   ============================================================ */

    @Query("""
       SELECT p
       FROM PaySlip p
       WHERE p.employee.id = :employeeId
       AND p.payDate BETWEEN :start AND :end
       AND p.versionNumber = (
            SELECT MAX(ps.versionNumber)
            FROM PaySlip ps
            WHERE ps.employee.id = :employeeId
            AND ps.payDate BETWEEN :start AND :end
       )
       """)
    Optional<PaySlip> findLatestByEmployeeAndPeriod(
            @Param("employeeId") Long employeeId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );


/* ============================================================
   LATEST PAYSLIP FOR ALL EMPLOYEES FOR PERIOD
   ============================================================ */

    @Query("""
       SELECT p
       FROM PaySlip p
       WHERE p.payDate BETWEEN :start AND :end
       AND p.versionNumber = (
            SELECT MAX(ps.versionNumber)
            FROM PaySlip ps
            WHERE ps.employee.id = p.employee.id
            AND ps.payDate BETWEEN :start AND :end
       )
       """)
    List<PaySlip> findLatestForAllEmployeesForPeriod(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
    @Query("""
       SELECT COALESCE(SUM(p.grossPay), 0)
       FROM PaySlip p
       WHERE p.employee.department.id = :departmentId
       AND p.versionNumber = (
            SELECT MAX(ps.versionNumber)
            FROM PaySlip ps
            WHERE ps.employee.id = p.employee.id
       )
       """)
    BigDecimal sumLatestGrossByDepartment(
            @Param("departmentId") Long departmentId
    );

}
