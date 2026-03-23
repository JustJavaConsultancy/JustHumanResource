package com.justjava.humanresource.kpi.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DepartmentKpiReportDTO {

    private Long departmentId;
    private String departmentName;

    private Long totalEmployees;
    private BigDecimal averageKpiScore;
}