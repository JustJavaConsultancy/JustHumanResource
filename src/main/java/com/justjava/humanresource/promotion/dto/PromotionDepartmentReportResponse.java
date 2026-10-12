package com.justjava.humanresource.promotion.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PromotionDepartmentReportResponse {
    Long departmentId;
    String departmentName;
    long requestCount;
    long implementedCount;
    BigDecimal monthlySalaryImpact;
    BigDecimal annualSalaryImpact;
}
