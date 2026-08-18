package com.justjava.humanresource.request.dto;
import com.justjava.humanresource.request.entity.*;
import lombok.Builder;
import lombok.Value;
import java.util.List;
@Value @Builder public class WorkflowRequestDetailDTO { WorkflowRequest request; Object typeDetails; List<WorkflowRequestItem> items; List<WorkflowRequestAttachment> attachments; List<WorkflowRequestApprovalStep> approvalSteps; List<WorkflowRequestComment> comments; List<WorkflowRequestActivity> activities; }
