package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "workflow_request_recommendations",
        indexes = @Index(name = "idx_request_recommendation_request",
                columnList = "workflowRequestId, createdAt, id"))
public class WorkflowRequestRecommendation extends BaseEntity {

    @Column(nullable = false)
    private Long workflowRequestId;

    @Column(nullable = false)
    private Long authorEmployeeId;

    @Column(nullable = false, length = 3000)
    private String text;
}
