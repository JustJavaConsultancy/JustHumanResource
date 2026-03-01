package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class PaySlipDTO {
    Long id;
    Long employeeId;
    String employeeName;
    Long payrollRunId;
    LocalDate payDate;
    Integer versionNumber;

    /* ======================
       SUMMARY SNAPSHOT
       ====================== */

    BigDecimal basicSalary;
    BigDecimal grossPay;
    BigDecimal totalDeductions;
    BigDecimal netPay;

    /* ======================
       BREAKDOWN
       ====================== */

    List<PaySlipLineDTO> allowances;
    List<PaySlipLineDTO> deductions;

    /* ======================
       STATUTORY SNAPSHOT
       ====================== */

    String appliedTaxBandSummary;
    String appliedPensionSchemeName;
    BigDecimal pensionAmount;
    PayrollRunStatus status;
    String bankName;
    String bankAccountNumber;
}