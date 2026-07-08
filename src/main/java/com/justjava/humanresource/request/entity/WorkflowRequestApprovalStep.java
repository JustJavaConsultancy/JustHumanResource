package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.ApprovalDecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @Entity @Table(name="workflow_request_approval_steps")
public class WorkflowRequestApprovalStep extends BaseEntity {
    @Column(nullable=false) private Long workflowRequestId;
    @Column(nullable=false) private Integer sequenceNo;
    @Column(nullable=false) private Long approverEmployeeId;
    private String approverRole;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ApprovalDecision decision = ApprovalDecision.PENDING;
    @Column(length=2000) private String comments;
    private LocalDateTime decisionAt;
    @Column(length=100) private String flowableTaskId;
}
