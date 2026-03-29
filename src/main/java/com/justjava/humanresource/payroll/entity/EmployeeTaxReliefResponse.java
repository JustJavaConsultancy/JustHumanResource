package com.justjava.humanresource.payroll.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class EmployeeTaxReliefResponse {

    private Long id;
    private Long employeeId;

    private String reliefCode;
    private String reliefName;

    private boolean overridden;
    private BigDecimal overrideAmount;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}