package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BalancedScorecardSummaryDTO {
    private Long perspectiveKpiId;
    private String perspectiveName;
    private BigDecimal weight;
    private BigDecimal possibleScore;
    private BigDecimal achievedScore;
}
