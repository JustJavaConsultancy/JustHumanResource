package com.justjava.humanresource.hr.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DepartmentSummaryDTO {

    private Long departmentId;
    private String departmentName;

    private Long totalEmployees;

    private BigDecimal totalGrossSalary;

    private BigDecimal averageKpiScore;
}
