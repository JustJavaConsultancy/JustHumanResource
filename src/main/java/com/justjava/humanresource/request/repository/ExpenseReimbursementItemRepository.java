package com.justjava.humanresource.request.repository;

import com.justjava.humanresource.request.entity.ExpenseReimbursementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseReimbursementItemRepository extends JpaRepository<ExpenseReimbursementItem, Long> {
    List<ExpenseReimbursementItem> findByWorkflowRequestIdOrderByExpenseDateAscIdAsc(Long workflowRequestId);
    long countByWorkflowRequestId(Long workflowRequestId);
}
