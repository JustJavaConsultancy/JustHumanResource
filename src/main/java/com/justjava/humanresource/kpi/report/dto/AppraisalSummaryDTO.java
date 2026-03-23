package com.justjava.humanresource.kpi.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AppraisalSummaryDTO {

    private Long employeeId;
    private String employeeName;

    private String cycleName;

    private BigDecimal kpiScore;
    private BigDecimal managerScore;
    private BigDecimal finalScore;

    private String outcome;
}