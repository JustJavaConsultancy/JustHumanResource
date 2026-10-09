package com.justjava.humanresource.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Before/after comparison for one pending Finance lock-approval task.
 * "Before" = what HR locked. "After" = what exists now.
 */
@Data
@Builder
public class PayrollLockChangeReportDTO {

    private String taskId;
    private Long periodId;

    /** The cutoff: creation time of the Finance task. */
    private LocalDateTime lockedAt;

    private boolean hasChanges;

    /** True when periodId could not be read; then hasChanges is always false. */
    private boolean unavailable;

    private int changedEmployeeCount;
    private int addedEmployeeCount;
    private int removedEmployeeCount;
    private int recalculatingEmployeeCount;

    /** Employees whose totals match but whose pay items differ. */
    private int lineItemsChangedEmployeeCount;

    private BigDecimal beforeTotalGross;
    private BigDecimal beforeTotalDeductions;
    private BigDecimal beforeTotalNet;

    private BigDecimal afterTotalGross;
    private BigDecimal afterTotalDeductions;
    private BigDecimal afterTotalNet;

    private BigDecimal grossTotalDiff;
    private BigDecimal deductionsTotalDiff;
    private BigDecimal netTotalDiff;

    /** Banner headline; null when hasChanges is false. */
    private String summaryMessage;

    private List<PayrollLockEmployeeChangeDTO> employees;
}