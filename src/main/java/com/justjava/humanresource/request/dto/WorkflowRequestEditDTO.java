package com.justjava.humanresource.request.dto;

import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.request.enums.RequestPriority;
import com.justjava.humanresource.request.enums.RequestStatus;
import com.justjava.humanresource.request.enums.RequestType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class WorkflowRequestEditDTO {
    Long id;
    String requestNumber;
    RequestType requestType;
    String title;
    String description;
    RequestPriority priority;
    Long departmentId;
    RequestStatus status;
    ApprovalRouteType approvalRouteType;
    LocalDateTime submittedAt;
    boolean canEdit;
    boolean canSubmit;
    boolean canSendFreeRoute;

    CreateWorkflowRequestCommand.StaffRequisitionPayload staffRequisition;
    CreateWorkflowRequestCommand.FileRequestPayload fileRequest;
    CreateWorkflowRequestCommand.AssetRequestPayload assetRequest;
    CreateWorkflowRequestCommand.ExpenseReimbursementPayload expenseReimbursement;
    List<CreateWorkflowRequestCommand.Item> items;
    List<Long> copiedEmployeeIds;
    List<RequestLookupOption> copiedEmployeeOptions;
}