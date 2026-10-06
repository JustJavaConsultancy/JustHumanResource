package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.LoanRepaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepaymentTransactionRepository extends JpaRepository<LoanRepaymentTransaction, Long> {

    List<LoanRepaymentTransaction> findByLoanAccountIdOrderByTransactionMonthAscIdAsc(Long loanAccountId);

    List<LoanRepaymentTransaction> findByRepaymentScheduleId(Long repaymentScheduleId);

    /** Idempotency guard for payroll retries/recalculation. */
    boolean existsByRepaymentScheduleIdAndPayrollRunId(Long repaymentScheduleId, Long payrollRunId);

    Optional<LoanRepaymentTransaction> findByRepaymentScheduleIdAndPayrollRunId(Long repaymentScheduleId, Long payrollRunId);

    List<LoanRepaymentTransaction> findByPayrollRunId(Long payrollRunId);
}
