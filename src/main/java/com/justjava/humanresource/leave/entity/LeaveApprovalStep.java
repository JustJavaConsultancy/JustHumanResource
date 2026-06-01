package com.justjava.humanresource.leave.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.leave.enums.LeaveApprovalDecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "leave_approval_steps")
public class LeaveApprovalStep extends BaseEntity {

    @Column(nullable = false)
    private Long leaveRequestId;

    @Column(nullable = false)
    private Integer sequenceNo;

    @Column(nullable = false)
    private Long approverEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveApprovalDecision decision;

    @Column(length = 1000)
    private String comments;

    private LocalDateTime decisionAt;

    @Column(length = 100)
    private String flowableTaskId;
}
