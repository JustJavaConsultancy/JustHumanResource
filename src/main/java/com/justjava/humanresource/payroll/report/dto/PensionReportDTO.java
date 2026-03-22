package com.justjava.humanresource.payroll.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PensionReportDTO {

    private Long employeeId;
    private String employeeName;
    private BigDecimal employeeContribution;
    private BigDecimal employerContribution;
    private String pensionScheme;
}
