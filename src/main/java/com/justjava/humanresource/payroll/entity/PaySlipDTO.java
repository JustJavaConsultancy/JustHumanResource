package com.justjava.humanresource.payroll.entity;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class PaySlipDTO {

    Long id;

    Long employeeId;

    Long payrollRunId;

    LocalDate payDate;

    BigDecimal grossPay;

    BigDecimal totalDeductions;

    BigDecimal netPay;
}
