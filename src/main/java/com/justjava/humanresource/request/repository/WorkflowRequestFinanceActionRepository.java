package com.justjava.humanresource.request.repository;

import com.justjava.humanresource.request.entity.WorkflowRequestFinanceAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowRequestFinanceActionRepository extends JpaRepository<WorkflowRequestFinanceAction, Long> {

    List<WorkflowRequestFinanceAction> findByWorkflowRequestIdOrderByCreatedAtAscIdAsc(Long workflowRequestId);

    /** The single final Finance decision for a request, if one has been recorded. */
    Optional<WorkflowRequestFinanceAction> findByWorkflowRequestIdAndFinalDecisionFlagTrue(Long workflowRequestId);

    boolean existsByWorkflowRequestIdAndFinalDecisionFlagTrue(Long workflowRequestId);
}
