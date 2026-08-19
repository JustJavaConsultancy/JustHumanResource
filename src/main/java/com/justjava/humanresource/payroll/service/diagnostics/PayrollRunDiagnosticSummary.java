package com.justjava.humanresource.payroll.service.diagnostics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PayrollRunDiagnosticSummary {
    private Long payrollRunId;
    private Long employeeId;
    private String employeeNumber;
    private String employeeName;
    private BigDecimal grossPay;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
    private BigDecimal includedLineItemsTotal;
    private BigDecimal skippedLineItemsTotal;
}
