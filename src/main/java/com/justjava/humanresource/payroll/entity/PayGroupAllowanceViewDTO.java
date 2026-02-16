package com.justjava.humanresource.payroll.entity;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PayGroupAllowanceViewDTO {

    private Long allowanceId;
    private String allowanceCode;
    private String allowanceName;
    private BigDecimal overrideAmount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
