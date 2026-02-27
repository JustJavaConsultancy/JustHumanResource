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
    Long payrollRunId;
    LocalDate payDate;

    String employeeName;

    Integer versionNumber;

    /* ======================
       SUMMARY
       ====================== */

    BigDecimal basicSalary;
    BigDecimal grossPay;
    BigDecimal totalDeductions;
    BigDecimal netPay;


    PayrollRunStatus status;

    /* ======================
       BREAKDOWN
       ====================== */

    List<PaySlipLineDTO> allowances;
    List<PaySlipLineDTO> deductions;

    /* ======================
       STATUTORY DETAILS
       ====================== */

    BigDecimal payeAmount;
    String taxBandSummary;

    BigDecimal pensionAmount;
    String pensionSchemeName;
}