package com.justjava.humanresource.payroll.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveTo;
}
