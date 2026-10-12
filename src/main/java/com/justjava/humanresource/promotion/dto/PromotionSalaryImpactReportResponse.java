package com.justjava.humanresource.promotion.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PromotionSalaryImpactReportResponse {
    long requestCount;
    long approvedOrImplementedCount;
    BigDecimal monthlySalaryImpact;
    BigDecimal annualSalaryImpact;
    BigDecimal averageSalaryIncreasePercent;
}
