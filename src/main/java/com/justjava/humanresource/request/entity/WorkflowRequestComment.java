package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="workflow_request_comments")
public class WorkflowRequestComment extends BaseEntity {
    @Column(nullable=false) private Long workflowRequestId;
    @Column(nullable=false, length=3000) private String comment;
    @Column(nullable=false) private Long commentedByEmployeeId;
    @Column(nullable=false) private boolean internalOnly;
}
