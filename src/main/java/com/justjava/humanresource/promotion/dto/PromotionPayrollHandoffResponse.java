package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionPayrollHandoffStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class PromotionPayrollHandoffResponse {
    Long id;
    PromotionPayrollHandoffStatus status;
    LocalDate effectiveDate;
    BigDecimal currentSalary;
    BigDecimal proposedSalary;
    BigDecimal monthlyPayrollImpact;
    BigDecimal annualPayrollImpact;
    String payGroupName;
    String createdByName;
    String appliedByName;
    LocalDateTime createdAt;
    LocalDateTime acknowledgedAt;
    LocalDateTime appliedAt;
    boolean externalApplicationOnly;
    String note;
}
