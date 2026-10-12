package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class PromotionApprovalStepResponse {
    Long id;
    PromotionApprovalStage stage;
    PromotionApprovalDecision decision;
    Long approverId;
    String approverName;
    Long originalApproverId;
    String originalApproverName;
    Long reassignedById;
    String reassignedByName;
    LocalDateTime reassignedAt;
    String reassignmentComment;
    Long delegatedById;
    String delegatedByName;
    LocalDateTime delegatedAt;
    String delegationComment;
    Long escalatedToApproverId;
    String escalatedToApproverName;
    String escalatedToGroup;
    LocalDateTime lastReminderSentAt;
    Integer reminderCount;
    LocalDateTime escalatedAt;
    String escalationComment;
    String approverGroup;
    Integer sequenceNumber;
    LocalDateTime assignedAt;
    LocalDateTime actedAt;
    String comment;
}
