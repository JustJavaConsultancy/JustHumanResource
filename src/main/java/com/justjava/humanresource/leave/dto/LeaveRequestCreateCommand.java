package com.justjava.humanresource.leave.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveRequestCreateCommand {
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private Long standInEmployeeId;
}
