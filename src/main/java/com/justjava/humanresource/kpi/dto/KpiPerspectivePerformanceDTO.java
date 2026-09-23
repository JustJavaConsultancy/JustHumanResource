package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class KpiPerspectivePerformanceDTO {
    private Long perspectiveId;
    private String perspectiveName;
    private BigDecimal averageScore;
    private BigDecimal totalWeightedScore;
    private long scoredLines;
}
