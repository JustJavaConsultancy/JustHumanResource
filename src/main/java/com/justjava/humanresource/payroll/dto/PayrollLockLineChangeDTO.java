package com.justjava.humanresource.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One pay item (line item) that differs between the lock-time run and the
 * latest run of an employee. Plain values only.
 */
@Data
@Builder
public class PayrollLockLineChangeDTO {

    private String componentType;
    private String componentCode;
    private String description;

    /** CHANGED, ADDED or REMOVED. */
    private String changeKind;

    /** Null when the item did not exist at lock. */
    private BigDecimal beforeAmount;

    /** Null when the item no longer exists. */
    private BigDecimal afterAmount;

    /** after minus before, null counted as zero. */
    private BigDecimal diff;
}
