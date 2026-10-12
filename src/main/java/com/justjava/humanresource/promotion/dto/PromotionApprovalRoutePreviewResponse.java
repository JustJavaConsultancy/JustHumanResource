package com.justjava.humanresource.promotion.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PromotionApprovalRoutePreviewResponse {
    Long promotionRequestId;
    String requestNumber;
    List<PromotionApprovalStepResponse> steps;
    List<String> matchedRoutingRules;
}
