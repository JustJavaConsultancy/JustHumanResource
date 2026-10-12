package com.justjava.humanresource.promotion.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PromotionSalaryImpactResponse {
    Long employeeId;
    String employeeName;
    Long proposedRoleId;
    String proposedRoleName;
    BigDecimal currentSalary;
    BigDecimal proposedSalary;
    BigDecimal salaryIncreaseAmount;
    BigDecimal salaryIncreasePercent;
    BigDecimal monthlyPayrollImpact;
    BigDecimal annualPayrollImpact;
}
