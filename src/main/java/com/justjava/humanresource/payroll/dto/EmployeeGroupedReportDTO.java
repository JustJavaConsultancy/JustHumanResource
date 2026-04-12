package com.justjava.humanresource.payroll.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class EmployeeGroupedReportDTO {

    private String groupName;

    private Long employeeCount;

    private BigDecimal totalGross;
    private BigDecimal totalDeductions;
    private BigDecimal totalNet;

    private BigDecimal paye;
    private BigDecimal pension;

    private List<EmployeeReportItemDTO> employees = new ArrayList<>();
}