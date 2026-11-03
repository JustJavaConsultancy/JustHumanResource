package com.justjava.humanresource.request.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkflowRequestInboxTaskDTO {

    private String taskId;
    private String taskName;
    private LocalDateTime createdTime;
    private boolean groupedApproval;
    private String approvalLevelLabel;
}
