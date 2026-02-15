package com.justjava.humanresource.payroll.entity;


import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class AllowanceAttachmentRequest {

    private Long allowanceId;
    private boolean overridden;
    private BigDecimal overrideAmount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
