package com.justjava.humanresource.request.repository;

import com.justjava.humanresource.request.entity.WorkflowRequestFinanceAction;
import com.justjava.humanresource.request.enums.FinanceActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkflowRequestFinanceActionRepository extends JpaRepository<WorkflowRequestFinanceAction, Long> {

    List<WorkflowRequestFinanceAction> findByWorkflowRequestIdOrderByCreatedAtAscIdAsc(Long workflowRequestId);

    /** The single final Finance decision for a request, if one has been recorded. */
    Optional<WorkflowRequestFinanceAction> findByWorkflowRequestIdAndFinalDecisionFlagTrue(Long workflowRequestId);

    boolean existsByWorkflowRequestIdAndFinalDecisionFlagTrue(Long workflowRequestId);

    boolean existsByWorkflowRequestIdAndActorEmployeeIdAndActionTypeIn(Long workflowRequestId,
                                                                       Long actorEmployeeId,
                                                                       Collection<FinanceActionType> actionTypes);

    @Modifying
    @Query("update WorkflowRequestFinanceAction a set a.finalDecisionFlag = null " +
            "where a.workflowRequestId = :requestId and a.actionType = :actionType and a.finalDecisionFlag = true")
    int clearLegacyReturnFinalFlags(@Param("requestId") Long requestId,
                                    @Param("actionType") FinanceActionType actionType);
}
