package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** A step is pending while its decision is null. */
@Repository
public interface EmployeeLoanApprovalStepRepository extends JpaRepository<EmployeeLoanApprovalStep, Long> {

    List<EmployeeLoanApprovalStep> findByLoanApplicationIdOrderBySequenceNoAscIdAsc(Long loanApplicationId);

    List<EmployeeLoanApprovalStep> findByLoanApplicationIdAndDecisionIsNull(Long loanApplicationId);

    /** Custom approver's pending tasks. */
    List<EmployeeLoanApprovalStep> findByApproverEmployeeIdAndDecisionIsNull(Long approverEmployeeId);

    Optional<EmployeeLoanApprovalStep> findFirstByLoanApplicationIdAndDecisionIsNullOrderBySequenceNoAsc(Long loanApplicationId);

    Optional<EmployeeLoanApprovalStep> findByFlowableTaskId(String flowableTaskId);

    boolean existsByLoanApplicationIdAndDecisionIsNull(Long loanApplicationId);
}
