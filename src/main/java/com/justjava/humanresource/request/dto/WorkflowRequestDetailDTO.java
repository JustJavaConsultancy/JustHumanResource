package com.justjava.humanresource.request.dto;
import com.justjava.humanresource.request.entity.*;
import lombok.Builder;
import lombok.Value;
import java.util.List;
@Value @Builder public class WorkflowRequestDetailDTO { WorkflowRequest request; Object typeDetails; List<WorkflowRequestItem> items; List<WorkflowRequestAttachment> attachments; List<WorkflowRequestApprovalStep> approvalSteps; List<com.justjava.humanresource.request.grouped.GroupedApprovalService.CycleView> groupedApprovalCycles; List<WorkflowRequestComment> comments; List<WorkflowRequestActivity> activities; List<WorkflowRequestFreeRouteEvent> freeRouteEvents; List<WorkflowRequestFinanceActionDTO> financeActions; List<RequestLookupOption> copiedEmployees; List<WorkflowRequestRecommendationDTO> recommendations; boolean canRecommend; boolean canManageCopiedEmployees; }