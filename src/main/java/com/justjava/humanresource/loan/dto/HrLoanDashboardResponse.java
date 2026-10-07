package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Summary cards, status breakdown, product usage, and exposure for /loans. */
@Value
@Builder
public class HrLoanDashboardResponse {
    long totalProducts;
    long activeProducts;

    long pendingHrApprovalCount;
    /** Applications currently waiting on configured custom approvers. */
    long pendingCustomApprovalCount;
    long pendingFinanceApprovalCount;
    long returnedCount;
    long rejectedCount;

    /** Approved outside-payroll loans waiting for Finance payment confirmation (not active yet). */
    long pendingDisbursementCount;
    BigDecimal pendingDisbursementAmount;

    long activeLoanCount;
    long completedLoanCount;
    BigDecimal totalOutstandingBalance;
    BigDecimal totalMonthlyDeduction;

    long missedDeductionCount;
    BigDecimal missedDeductionAmount;

    @Builder.Default Map<LoanApplicationStatus, Long> applicationsByStatus = new LinkedHashMap<>();
    @Builder.Default List<ProductUsage> productUsage = new ArrayList<>();
    /** Employees with the largest outstanding exposure. */
    @Builder.Default List<EmployeeLoanExposureResponse> topExposures = new ArrayList<>();

    @Value
    @Builder
    public static class ProductUsage {
        Long loanProductId;
        String loanProductName;
        long applicationCount;
        long activeLoanCount;
        BigDecimal outstandingBalance;
    }
}