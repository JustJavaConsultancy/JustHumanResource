package com.justjava.humanresource.payroll.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PayGroupTaxReliefResponse {

    private Long id;
    private Long payGroupId;

    private String reliefCode;
    private String reliefName;

    private BigDecimal overrideAmount;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}