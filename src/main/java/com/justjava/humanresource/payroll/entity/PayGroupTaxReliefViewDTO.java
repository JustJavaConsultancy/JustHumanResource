package com.justjava.humanresource.payroll.entity;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PayGroupTaxReliefViewDTO {

    private Long taxReliefId;
    private String taxReliefCode;
    private String taxReliefName;
    private BigDecimal overrideAmount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}

