package com.justjava.humanresource.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PayrollItemDTO {

    private String code;
    private String description;
    private BigDecimal amount;
    private boolean taxable;
    private boolean outOfPayroll;
}