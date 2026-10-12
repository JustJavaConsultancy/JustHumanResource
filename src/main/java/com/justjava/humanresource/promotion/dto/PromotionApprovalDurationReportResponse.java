package com.justjava.humanresource.promotion.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PromotionApprovalDurationReportResponse {
    long approvedRequestCount;
    BigDecimal averageApprovalDays;
    BigDecimal fastestApprovalDays;
    BigDecimal slowestApprovalDays;
}
