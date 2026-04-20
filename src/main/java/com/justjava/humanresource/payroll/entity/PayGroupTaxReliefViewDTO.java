package com.justjava.humanresource.payroll.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveTo;
}

