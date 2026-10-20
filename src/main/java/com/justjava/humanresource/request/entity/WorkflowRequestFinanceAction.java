package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.FinanceActionType;
import com.justjava.humanresource.request.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Append-only audit log of Finance override actions (forwards and final decisions).
 * Rows are only ever inserted; they are never updated or deleted, so repeated forwards
 * keep their full history.
 *
 * One-final-decision invariant (database level):
 *  - {@code finalDecisionFlag} is set to TRUE only on APPROVE / REJECT / RETURN rows and is
 *    left NULL on FORWARD rows.
 *  - The unique constraint on (workflow_request_id, final_decision_flag) therefore allows any
 *    number of FORWARD rows (NULLs are distinct in PostgreSQL) but at most ONE final decision
 *    row per request. A concurrent second decision fails on insert even if the application
 *    level lock/flag check were bypassed.
 */
@Getter @Setter @Entity
@Table(name = "workflow_request_finance_actions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_finance_action_one_final_decision",
                columnNames = {"workflowRequestId", "finalDecisionFlag"}),
        indexes = @Index(name = "idx_finance_action_request", columnList = "workflowRequestId"))
public class WorkflowRequestFinanceAction extends BaseEntity {

    @Column(nullable = false)
    private Long workflowRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinanceActionType actionType;

    /** Finance employee who performed the action. */
    @Column(nullable = false)
    private Long actorEmployeeId;

    /** Forward recipient (FORWARD only). */
    private Long targetEmployeeId;

    @Column(length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private RequestStatus statusBefore;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private RequestStatus statusAfter;

    /** Number of pending configured approval steps superseded by this action. */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer supersededStepCount = 0;

    /** TRUE for a final decision row, NULL for FORWARD rows. Never set to FALSE. */
    private Boolean finalDecisionFlag;
}
