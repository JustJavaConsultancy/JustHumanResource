package com.justjava.humanresource.approval.entity;

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
@Table(
        name = "custom_approval_path_steps",
        indexes = {
                @Index(name = "idx_custom_path_steps_path", columnList = "customApprovalPathId")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_custom_path_sequence", columnNames = {"customApprovalPathId", "sequenceNo"})
        }
)
public class CustomApprovalPathStep extends BaseEntity {
    @Column(nullable = false)
    private Long customApprovalPathId;

    @Column(nullable = false)
    private Integer sequenceNo;

    @Column(nullable = false)
    private Long approverEmployeeId;
}
