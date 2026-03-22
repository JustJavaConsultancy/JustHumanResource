package com.justjava.humanresource.payroll.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PayeReportDTO {
    private Long employeeId;
    private String employeeName;
    private BigDecimal taxableIncome;
    private BigDecimal paye;
    private BigDecimal ytdPaye;
}