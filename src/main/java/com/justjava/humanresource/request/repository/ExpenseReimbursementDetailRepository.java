package com.justjava.humanresource.request.repository;

import com.justjava.humanresource.request.entity.ExpenseReimbursementDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExpenseReimbursementDetailRepository extends JpaRepository<ExpenseReimbursementDetail, Long> {
    Optional<ExpenseReimbursementDetail> findByWorkflowRequestId(Long workflowRequestId);
}
