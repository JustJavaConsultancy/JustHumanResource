package com.justjava.humanresource.payroll.entity;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class EmployeeDeductionResponse {

    private Long id;
    private Long employeeId;
    private Long deductionId;
    private boolean overridden;
    private BigDecimal overrideAmount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
