package com.justjava.humanresource.payroll.entity;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PaySlipLineDTO {

    String code;
    String description;
    BigDecimal amount;
    boolean taxable;
}
