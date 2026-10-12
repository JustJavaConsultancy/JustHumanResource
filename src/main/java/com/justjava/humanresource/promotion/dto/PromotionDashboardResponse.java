package com.justjava.humanresource.promotion.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class PromotionDashboardResponse {
    long pendingApprovalCount;
    long approvedCount;
    long implementedCount;
    long rejectedCount;
    BigDecimal monthlySalaryImpact;
    BigDecimal annualSalaryImpact;
    List<PromotionSummaryResponse> recentRequests;
}
