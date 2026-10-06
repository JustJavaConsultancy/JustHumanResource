package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.LoanRepaymentSchedule;
import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepaymentScheduleRepository extends JpaRepository<LoanRepaymentSchedule, Long> {

    List<LoanRepaymentSchedule> findByLoanAccountIdOrderBySequenceNumberAsc(Long loanAccountId);

    Optional<LoanRepaymentSchedule> findByLoanAccountIdAndDueMonth(Long loanAccountId, LocalDate dueMonth);

    List<LoanRepaymentSchedule> findByDueMonthAndStatusIn(LocalDate dueMonth, Collection<LoanRepaymentStatus> statuses);

    List<LoanRepaymentSchedule> findByDueMonth(LocalDate dueMonth);

    List<LoanRepaymentSchedule> findByStatusOrderByDueMonthDesc(LoanRepaymentStatus status);

    List<LoanRepaymentSchedule> findByLoanAccountIdAndStatus(Long loanAccountId, LoanRepaymentStatus status);

    List<LoanRepaymentSchedule> findByLoanAccountIdInAndStatus(Collection<Long> loanAccountIds, LoanRepaymentStatus status);

    boolean existsByLoanAccountId(Long loanAccountId);
}
