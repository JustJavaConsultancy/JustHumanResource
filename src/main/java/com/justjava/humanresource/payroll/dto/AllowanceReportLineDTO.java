package com.justjava.humanresource.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AllowanceReportLineDTO {


    private String code;

    private String description;

    private BigDecimal totalAmount;

    private boolean taxable;
    private boolean partOfGross;
}