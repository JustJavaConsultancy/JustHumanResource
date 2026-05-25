package com.justjava.humanresource.leave.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.leave.enums.LeaveRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "leave_requests")
public class LeaveRequest extends BaseEntity {

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private Long standInEmployeeId;

    @Column(nullable = false, length = 50)
    private String leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer totalDays;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeaveRequestStatus status;

    @Column(length = 100)
    private String workflowInstanceId;

    private Integer currentApprovalLevel;
    private Integer totalApprovalLevels;
}
