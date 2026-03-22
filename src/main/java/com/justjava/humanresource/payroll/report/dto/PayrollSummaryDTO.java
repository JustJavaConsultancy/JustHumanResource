package com.justjava.humanresource.payroll.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PayrollSummaryDTO {

    private String groupName; // company / department / payGroup

    private BigDecimal totalGross;
    private BigDecimal totalDeductions;
    private BigDecimal totalNet;
    private BigDecimal totalPaye;
    private BigDecimal totalPension;
}