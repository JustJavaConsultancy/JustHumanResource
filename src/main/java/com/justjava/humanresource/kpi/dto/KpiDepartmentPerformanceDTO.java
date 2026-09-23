package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class KpiDepartmentPerformanceDTO {
    private Long departmentId;
    private String departmentName;
    private long totalAppraisals;
    private long completedAppraisals;
    private BigDecimal averageScore;
    private BigDecimal completionRate;
}
