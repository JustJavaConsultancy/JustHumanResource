package com.justjava.humanresource.leave.dto;

import lombok.Data;

@Data
public class LeaveApprovalActionCommand {
    private String taskId;
    private String comment;
}
