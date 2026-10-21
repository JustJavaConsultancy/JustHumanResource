package com.justjava.humanresource.request.dto;

import com.justjava.humanresource.request.entity.WorkflowRequest;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkflowRequestInboxItemDTO {

    private WorkflowRequest request;
    private boolean requester;
    private boolean copied;
    private boolean approvalRequired;
    private boolean financeForwardRecipient;
    private List<WorkflowRequestInboxTaskDTO> approvalTasks;
}
