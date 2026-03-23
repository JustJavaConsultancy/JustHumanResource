package com.justjava.humanresource.kpi.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EmployeeKpiScorecardDTO {

    private Long employeeId;
    private String employeeName;
    private String kpiCode;
    private String kpiName;

    private BigDecimal targetValue;
    private BigDecimal actualValue;

    private BigDecimal score;
    private BigDecimal weight;
    private BigDecimal weightedScore;
}