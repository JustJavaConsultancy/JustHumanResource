package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "workflow_request_copied_employees",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_workflow_request_copied_employee",
                columnNames = {"workflowRequestId", "employeeId"}),
        indexes = {
                @Index(name = "idx_request_copied_request", columnList = "workflowRequestId"),
                @Index(name = "idx_request_copied_employee", columnList = "employeeId")
        })
public class WorkflowRequestCopiedEmployee extends BaseEntity {

    @Column(nullable = false)
    private Long workflowRequestId;

    @Column(nullable = false)
    private Long employeeId;
}
