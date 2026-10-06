package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeLoanApplicationRepository extends JpaRepository<EmployeeLoanApplication, Long> {

    Optional<EmployeeLoanApplication> findByApplicationNumber(String applicationNumber);

    List<EmployeeLoanApplication> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<EmployeeLoanApplication> findByEmployeeIdAndStatusOrderByCreatedAtDesc(Long employeeId, LoanApplicationStatus status);

    List<EmployeeLoanApplication> findByEmployeeIdAndStatusIn(Long employeeId, Collection<LoanApplicationStatus> statuses);

    List<EmployeeLoanApplication> findByStatusOrderBySubmittedAtAsc(LoanApplicationStatus status);

    List<EmployeeLoanApplication> findAllByOrderByCreatedAtDesc();

    long countByLoanProductId(Long loanProductId);

    boolean existsByLoanProductId(Long loanProductId);

    Optional<EmployeeLoanApplication> findByIdAndEmployeeId(Long id, Long employeeId);

    Optional<EmployeeLoanApplication> findByWorkflowInstanceId(String workflowInstanceId);

    @Query("select a from EmployeeLoanApplication a where a.id in :ids order by a.submittedAt asc")
    List<EmployeeLoanApplication> findByIdsOrderBySubmittedAt(@Param("ids") Collection<Long> ids);

    /** HR pending queue. */
    default List<EmployeeLoanApplication> findHrPending() {
        return findByStatusOrderBySubmittedAtAsc(LoanApplicationStatus.PENDING_HR_APPROVAL);
    }

    /** Finance pending queue. */
    default List<EmployeeLoanApplication> findFinancePending() {
        return findByStatusOrderBySubmittedAtAsc(LoanApplicationStatus.PENDING_FINANCE_APPROVAL);
    }
}
