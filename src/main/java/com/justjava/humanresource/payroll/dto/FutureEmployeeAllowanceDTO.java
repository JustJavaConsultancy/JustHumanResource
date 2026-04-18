package com.justjava.humanresource.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class FutureEmployeeAllowanceDTO {

    private Long employeeId;
    private String firstName;
    private String secondName;

    private String allowanceCode;
    private String allowanceName;

    private BigDecimal amount;
    private Boolean overridden;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}