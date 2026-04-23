package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.dto.FutureEmployeeAllowanceDTO;
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
    BigDecimal nonGrossEarnings;

    /* ======================
       BREAKDOWN
       ====================== */

    List<PaySlipLineDTO> allowances;
    List<PaySlipLineDTO> deductions;
    List<FutureEmployeeAllowanceDTO> futureAllowances;

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