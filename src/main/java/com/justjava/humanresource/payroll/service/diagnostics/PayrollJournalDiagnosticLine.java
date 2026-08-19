package com.justjava.humanresource.payroll.service.diagnostics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PayrollJournalDiagnosticLine {
    private Long payrollRunId;
    private Long employeeId;
    private String employeeNumber;
    private String employeeName;
    private String componentType;
    private String componentCode;
    private String componentName;
    private BigDecimal payrollLineAmount;
    private String lineType;
    private String accountCode;
    private String accountName;
    private String accountSource;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String description;
    private String skipReason;

    public boolean isSkipped() {
        return skipReason != null && !skipReason.isBlank();
    }
}
