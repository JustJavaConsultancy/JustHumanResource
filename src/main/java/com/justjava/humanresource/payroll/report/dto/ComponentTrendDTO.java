package com.justjava.humanresource.payroll.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ComponentTrendDTO {

    private String period; // e.g. "2026-01"
    private String componentCode;
    private BigDecimal totalAmount;
}