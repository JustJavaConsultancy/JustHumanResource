package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionRequestSource;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PromotionRoutingRuleResponse {
    String name;
    Integer priority;
    Long departmentId;
    Long jobGradeId;
    String employeeCategory;
    PromotionRequestSource requestSource;
    Boolean financeApprovalRequired;
    Boolean executiveApprovalRequired;
}
