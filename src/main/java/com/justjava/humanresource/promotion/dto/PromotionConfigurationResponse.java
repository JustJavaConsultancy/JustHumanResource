package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class PromotionConfigurationResponse {
    Long id;
    String name;
    Integer minimumMonthsInCurrentRole;
    BigDecimal minimumAppraisalScore;
    boolean allowProbationPromotion;
    boolean requireFinanceApproval;
    boolean requireExecutiveApproval;
    BigDecimal maximumSalaryIncreasePercentWithoutExecutiveApproval;
    Integer approvalReminderAfterHours;
    Integer approvalEscalationAfterHours;
    Long escalationApproverId;
    String escalationApproverGroup;
    List<PromotionAttachmentType> requiredDocumentsBeforeSubmit;
    List<PromotionAttachmentType> requiredDocumentsBeforeImplementation;
    List<PromotionRoutingRuleResponse> routingRules;
    boolean active;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    String createdByName;
    String updatedByName;
    LocalDateTime deactivatedAt;
    String deactivatedByName;
}
