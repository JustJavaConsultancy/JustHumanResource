package com.justjava.humanresource.payroll.entity;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PayGroupDeductionViewDTO {

    private Long deductionId;
    private String deductionCode;
    private String deductionName;
    private BigDecimal overrideAmount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
