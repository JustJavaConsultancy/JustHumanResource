package com.justjava.humanresource.leave.dto;

import com.justjava.humanresource.leave.enums.LeaveRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LeaveRequestDetailDTO {
    private Long id;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalDays;
    private String reason;
    private LeaveRequestStatus status;
    private Integer currentApprovalLevel;
    private Integer totalApprovalLevels;
    private LocalDateTime createdAt;
    private Long requesterId;
    private String requesterName;
    private Long standInId;
    private String standInName;
}