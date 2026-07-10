package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="workflow_request_types")
public class WorkflowRequestType extends BaseEntity {
    @Column(nullable=false, unique=true, length=40) private String code;
    @Column(nullable=false, length=100) private String name;
    @Column(length=500) private String description;
    @Column(nullable=false) private boolean enabled = true;
    @Column(nullable=false) private boolean requiresApproval = true;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private ApprovalRouteType approvalRouteType = ApprovalRouteType.LINE_MANAGER;
    private Long customApprovalPathId;
    @Column(nullable=false) private boolean requiresAttachment;
    @Column(nullable=false) private boolean supportsItems;
    @Column(nullable=false, length=100) private String processDefinitionKey = "genericRequestApprovalProcess";
    @Column(length=100) private String handlerBeanName;
}
