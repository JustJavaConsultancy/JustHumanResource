package com.justjava.humanresource.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class PayrollRunDTO {

    private Long payrollRunId;

    private Long employeeId;
    private String employeeNumber;
    private String employeeName;

    private LocalDate payrollDate;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private BigDecimal grossPay;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;

    private BigDecimal paye;
    private BigDecimal pension;

    private BigDecimal ytdGross;
    private BigDecimal ytdNet;
    private BigDecimal ytdPaye;

    private String pensionScheme;

    private List<PayrollItemDTO> allowances;
    private List<PayrollItemDTO> deductions;
}