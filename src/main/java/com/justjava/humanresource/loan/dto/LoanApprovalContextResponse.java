package com.justjava.humanresource.loan.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Employee/decision context shown to HR, Finance, and assigned custom approvers.
 * Informational only; the system never auto-rejects from these values.
 */
@Value
@Builder
public class LoanApprovalContextResponse {
    // Employee
    Long employeeId;
    String employeeNumber;
    String employeeName;
    String email;
    Long departmentId;
    String departmentName;
    String jobTitle;
    String jobGradeName;
    String jobStepName;
    String employmentStatus;
    LocalDate employmentDate;
    /** Current gross salary (the application also stores a snapshot taken at submission). */
    BigDecimal grossSalary;
    BigDecimal grossSalarySnapshot;

    // Proposed deduction vs pay
    /** repaymentAmount of this application as a percentage of gross salary. */
    BigDecimal repaymentToGrossPercent;
    /** Existing monthly loan deductions plus this application's repayment amount. */
    BigDecimal projectedTotalMonthlyLoanDeduction;

    // Exposure
    EmployeeLoanExposureResponse exposure;

    /** Human-readable cautions, e.g. "Employee has 2 active loans (outstanding 450,000)". */
    @Builder.Default List<String> warnings = new ArrayList<>();
}
