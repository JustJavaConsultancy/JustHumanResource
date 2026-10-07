package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.LoanDisbursement;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanDisbursementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanDisbursementRepository
        extends JpaRepository<LoanDisbursement, Long>, JpaSpecificationExecutor<LoanDisbursement> {

    Optional<LoanDisbursement> findByLoanApplicationId(Long loanApplicationId);

    List<LoanDisbursement> findByStatusOrderByFinalApprovedAtAsc(LoanDisbursementStatus status);

    boolean existsByLoanApplicationId(Long loanApplicationId);

    /** Row lock so two Finance users (or a double click) cannot confirm the same payment twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from LoanDisbursement d where d.id = :id")
    Optional<LoanDisbursement> findByIdForUpdate(@Param("id") Long id);

    /**
     * PAYROLL_PERIOD disbursements to show on an employee's run: scheduled ones
     * not yet mapped to any run AND scheduled for this payroll period (so a loan
     * scheduled for June can never be picked up by July), plus ones already mapped
     * to a run of the same period (so recalculation and amendment runs keep the same line).
     */
    @Query("""
            select d from LoanDisbursement d
            where d.employeeId = :employeeId
              and d.method = :method
              and (
                    (d.status = :scheduled and d.payrollRunId is null and d.payrollPeriodId = :payrollPeriodId)
                 or (d.status in (:scheduled, :paid) and d.payrollRunId in (
                        select r.id from PayrollRun r
                        where r.employee.id = :employeeId
                          and r.periodStart = :periodStart
                          and r.periodEnd = :periodEnd))
              )
            order by d.id asc
            """)
    List<LoanDisbursement> findPayrollDisbursementsForRun(
            @Param("employeeId") Long employeeId,
            @Param("method") LoanDisbursementMethod method,
            @Param("scheduled") LoanDisbursementStatus scheduled,
            @Param("paid") LoanDisbursementStatus paid,
            @Param("payrollPeriodId") Long payrollPeriodId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    // ---- dashboard aggregates (Step 14)

    long countByMethodAndStatus(LoanDisbursementMethod method, LoanDisbursementStatus status);

    /** Sum of disbursement amounts for a method/status; null when there are no rows. */
    @Query("select sum(d.amount) from LoanDisbursement d where d.method = :method and d.status = :status")
    BigDecimal sumAmountByMethodAndStatus(@Param("method") LoanDisbursementMethod method,
                                          @Param("status") LoanDisbursementStatus status);

    /** Sum of amounts paid in [from, to); null when there are no rows. */
    @Query("""
            select sum(d.amount) from LoanDisbursement d
            where d.method = :method and d.status = :status
              and d.paidAt >= :from and d.paidAt < :to
            """)
    BigDecimal sumAmountPaidBetween(@Param("method") LoanDisbursementMethod method,
                                    @Param("status") LoanDisbursementStatus status,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    // Filtered list / CSV queries use JpaSpecificationExecutor (see LoanDisbursementServiceImpl.toSpecification).
}