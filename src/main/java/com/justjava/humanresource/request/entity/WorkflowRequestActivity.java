package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.RequestActivityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="workflow_request_activities")
public class WorkflowRequestActivity extends BaseEntity {
    @Column(nullable=false) private Long workflowRequestId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RequestActivityType activityType;
    @Column(nullable=false, length=1000) private String description;
    private Long actorEmployeeId;
    @Column(columnDefinition="TEXT") private String metadataJson;
}
