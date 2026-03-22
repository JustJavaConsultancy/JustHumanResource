package com.justjava.humanresource.payroll.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ComponentBreakdownDTO {
    private String componentCode;
    private String componentName;
    private BigDecimal totalAmount;
}