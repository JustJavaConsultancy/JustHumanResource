package com.justjava.humanresource.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One employee row of the "payroll changed after lock" report.
 * Carries plain values only, so the template never triggers lazy loading.
 */
@Data
@Builder
public class PayrollLockEmployeeChangeDTO {

    private Long employeeId;
    private String employeeNumber;
    private String employeeName;

    private PayrollLockChangeType changeType;

    private Integer beforeVersion;
    private Integer afterVersion;

    /* At HR lock (null when there is no baseline) */
    private BigDecimal beforeGross;
    private BigDecimal beforeDeductions;
    private BigDecimal beforeNet;

    /* Now (null when the figures are not final) */
    private BigDecimal afterGross;
    private BigDecimal afterDeductions;
    private BigDecimal afterNet;

    /* after minus before (null when not comparable) */
    private BigDecimal grossDiff;
    private BigDecimal deductionsDiff;
    private BigDecimal netDiff;

    private boolean grossChanged;
    private boolean deductionsChanged;
    private boolean netChanged;

    /** Human sentence describing what changed. */
    private String description;

    /** Pay items that differ (empty when none, or when not compared). */
    @Builder.Default
    private List<PayrollLockLineChangeDTO> lineChanges = new ArrayList<>();
}