package com.justjava.humanresource.payroll.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Variance for a single payroll component (earning, deduction, or tax relief)
 * between the current period and the previous period.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineVarianceDTO {

    /** E.g. "BASIC", "HOUSING", "PAYE", "PENSION_EMP" */
    private String componentCode;

    /** Human-readable label, e.g. "Housing Allowance" */
    private String description;

    /** EARNING | DEDUCTION | TAX_RELIEF */
    private String componentType;

    /** Amount in the previous period — null when the component is new this month */
    private BigDecimal previousAmount;

    /** Amount in the current period — null when the component was removed */
    private BigDecimal currentAmount;

    /**
     * currentAmount − previousAmount.
     * Positive = increase, negative = decrease.
     * Uses BigDecimal.ZERO for absent sides so the arithmetic is always defined.
     */
    private BigDecimal variance;

    /** ADDED | REMOVED | CHANGED | UNCHANGED */
    private String changeType;
}
