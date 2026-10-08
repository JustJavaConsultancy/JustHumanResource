package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.EmployeeLoanAccount;
import com.justjava.humanresource.loan.enums.LoanAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeLoanAccountRepository extends JpaRepository<EmployeeLoanAccount, Long> {

    Optional<EmployeeLoanAccount> findByLoanApplicationId(Long loanApplicationId);

    List<EmployeeLoanAccount> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<EmployeeLoanAccount> findByEmployeeIdAndStatus(Long employeeId, LoanAccountStatus status);

    List<EmployeeLoanAccount> findByStatusOrderByCreatedAtDesc(LoanAccountStatus status);

    List<EmployeeLoanAccount> findAllByOrderByCreatedAtDesc();

    /** Number of loan accounts of one product in the given status (used for "active loans" on product screens). */
    long countByLoanProductIdAndStatus(Long loanProductId, LoanAccountStatus status);

    /** Active accounts for an employee whose repayment has started on or before the given month. */
    @Query("""
            select a from EmployeeLoanAccount a
            where a.employeeId = :employeeId
              and a.status = com.justjava.humanresource.loan.enums.LoanAccountStatus.ACTIVE
              and a.repaymentStartMonth <= :month
            """)
    List<EmployeeLoanAccount> findActiveDueForEmployee(@Param("employeeId") Long employeeId,
                                                       @Param("month") LocalDate month);

    /** All active accounts whose repayment has started on or before the given month. */
    @Query("""
            select a from EmployeeLoanAccount a
            where a.status = com.justjava.humanresource.loan.enums.LoanAccountStatus.ACTIVE
              and a.repaymentStartMonth <= :month
            """)
    List<EmployeeLoanAccount> findActiveDueInMonth(@Param("month") LocalDate month);
}