package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Summary cards, payroll impact, and exposure for /finance/loans. */
@Value
@Builder
public class FinanceLoanDashboardResponse {
    long pendingFinanceApprovalCount;
    long activeLoanCount;
    long completedLoanCount;

    /** Principal of all activated loans (active + completed). */
    BigDecimal totalPrincipalApproved;
    BigDecimal totalOutstandingBalance;
    BigDecimal outstandingPrincipal;
    BigDecimal outstandingInterest;
    BigDecimal totalRepaid;

    long missedDeductionCount;
    BigDecimal missedDeductionAmount;

    /** Outside-payroll loans fully approved and waiting for Finance to confirm payment. */
    long pendingDisbursementCount;
    BigDecimal pendingDisbursementAmount;
    /** Outside-payroll amount Finance confirmed as paid in the current calendar month. */
    BigDecimal paidDisbursementAmountThisMonth;

    @Builder.Default Map<LoanApplicationStatus, Long> applicationsByStatus = new LinkedHashMap<>();
    /** Expected loan deductions per upcoming payroll month. */
    @Builder.Default List<PayrollImpact> payrollImpact = new ArrayList<>();
    @Builder.Default List<EmployeeLoanExposureResponse> topExposures = new ArrayList<>();

    @Value
    @Builder
    public static class PayrollImpact {
        LocalDate month;
        BigDecimal expectedDeductionAmount;
        long loanCount;
        long employeeCount;
    }
}