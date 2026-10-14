package com.justjava.humanresource.utils;

import java.math.BigDecimal;
import java.time.LocalDate;


public record PayrollLockSummary(
        String companyName,
        String periodName,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalEmployees,
        BigDecimal grossPay,
        BigDecimal totalDeductions,
        BigDecimal netPay,
        BigDecimal payeTax,
        BigDecimal employeePension,
        String logoUrl
) {
}