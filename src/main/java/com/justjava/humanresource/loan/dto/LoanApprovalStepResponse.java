package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApprovalDecision;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** One approval step in the timeline (HR, Finance, or one custom approver level). */
@Value
@Builder
public class LoanApprovalStepResponse {
    Long id;
    Integer sequenceNo;
    LoanApprovalStage approvalStage;
    /** Display label, e.g. "HR Approval", "Finance Approval", "Custom Approval Level 2". */
    String stageLabel;
    Long approverEmployeeId;
    String approverName;
    /** Candidate group for role-based steps (e.g. humanresource, financialofficers). */
    String approverGroup;
    /** Null while the step has not been decided. */
    LoanApprovalDecision decision;
    boolean pending;
    /** True for the step currently waiting for action. */
    boolean current;
    String comments;
    LocalDateTime decisionAt;
    Long actedByEmployeeId;
    String actedByName;
}
