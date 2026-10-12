package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.entity.PromotionStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class PromotionSummaryResponse {
    Long id;
    String requestNumber;
    Long employeeId;
    String employeeName;
    String departmentName;
    String currentRoleName;
    String proposedRoleName;
    BigDecimal appraisalScore;
    PromotionStatus status;
    LocalDate effectiveDate;
    LocalDateTime requestedAt;
    BigDecimal salaryIncreaseAmount;
    BigDecimal salaryIncreasePercent;
}
