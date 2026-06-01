package com.justjava.humanresource.payroll.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One row of the Payroll Variance Report — represents a single employee's
 * comparison between the current period and the immediately preceding period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollVarianceDTO {

    // ── Employee identity ─────────────────────────────────────────────────────

    private Long   employeeId;
    private String employeeNumber;
    private String employeeName;
    private String department;

    // ── Current period ────────────────────────────────────────────────────────

    private BigDecimal currentGross;
    private BigDecimal currentDeductions;
    private BigDecimal currentNet;

    // ── Previous period (zero-filled when employee is new) ───────────────────

    private BigDecimal previousGross;
    private BigDecimal previousDeductions;
    private BigDecimal previousNet;

    // ── Variances (current − previous) ───────────────────────────────────────

    /** Positive = pay rise, negative = pay cut */
    private BigDecimal grossVariance;
    private BigDecimal deductionsVariance;
    /** Positive = more take-home, negative = less take-home */
    private BigDecimal netVariance;

    // ── Per-component breakdown ───────────────────────────────────────────────

    private List<LineVarianceDTO> lineVariances;

    // ── Change reasons (may contain multiple) ────────────────────────────────

    /**
     * Human-readable reasons detected for this employee's variance.
     * Examples: "Salary change", "Allowance added", "KPI adjustment",
     * "Promotion", "Amendment", "New employee".
     */
    private List<String> reasons;

    // ── Meta flags ────────────────────────────────────────────────────────────

    /** True when no previous-period run exists for this employee. */
    private boolean newEmployee;

    /** True when the current run is an AMENDMENT version. */
    private boolean amendment;

    /**
     * Free-text amendment reason from {@code PayrollRun.amendmentReason}.
     * Null for ORIGINAL runs or amendments where no reason was recorded.
     */
    private String amendmentReason;
}
