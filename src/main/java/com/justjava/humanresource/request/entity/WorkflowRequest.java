package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @Entity @Table(name = "workflow_requests", indexes = {
        @Index(name = "idx_request_requester", columnList = "requesterEmployeeId"),
        @Index(name = "idx_request_status_type", columnList = "status,requestType")})
public class WorkflowRequest extends BaseEntity {
    @Column(nullable=false, unique=true, length=30) private String requestNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private RequestType requestType;
    @Column(nullable=false, length=200) private String title;
    @Column(columnDefinition="TEXT") private String description;
    @Column(nullable=false) private Long requesterEmployeeId;
    private Long departmentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private RequestPriority priority = RequestPriority.NORMAL;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private RequestStatus status = RequestStatus.DRAFT;
    @Column(length=100) private String workflowInstanceId;
    private Integer currentApprovalLevel;
    private Integer totalApprovalLevels;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime closedAt;

    // Free Route metadata (Step 1)
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ApprovalRouteType approvalRouteType;

    private Long freeRouteCurrentApproverEmployeeId;
    private LocalDateTime freeRouteStartedAt;
    private LocalDateTime freeRouteLastForwardedAt;

    // Finance override state (Step 3). Event history lives in WorkflowRequestFinanceAction.
    /** Current Finance-forward recipient; null when no Finance handoff is pending. */
    private Long financeOverrideRecipientId;

    /** True once Finance has approved or rejected the request. A return is not final. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean financeDecisionConsumed = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FinanceActionType financeDecisionType;

    private Long financeDecisionByEmployeeId;
    private LocalDateTime financeDecisionAt;
}