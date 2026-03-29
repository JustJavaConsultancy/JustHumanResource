package com.justjava.humanresource.payroll.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TaxReliefAttachmentRequest {

    private Long taxReliefId;

    private boolean overridden;
    private BigDecimal overrideAmount;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}