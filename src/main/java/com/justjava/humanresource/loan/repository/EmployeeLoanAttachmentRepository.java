package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.EmployeeLoanAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeLoanAttachmentRepository extends JpaRepository<EmployeeLoanAttachment, Long> {

    List<EmployeeLoanAttachment> findByLoanApplicationIdOrderByCreatedAtAsc(Long loanApplicationId);

    long countByLoanApplicationId(Long loanApplicationId);

    boolean existsByLoanApplicationId(Long loanApplicationId);
}
