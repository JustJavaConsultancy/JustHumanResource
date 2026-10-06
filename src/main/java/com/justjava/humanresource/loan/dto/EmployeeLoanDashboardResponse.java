package com.justjava.humanresource.loan.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Summary cards for /employee/loans. */
@Value
@Builder
public class EmployeeLoanDashboardResponse {
    long draftCount;
    long pendingCount;
    long returnedCount;
    long approvedCount;
    long rejectedCount;
    long cancelledCount;
    long activeLoanCount;
    long completedLoanCount;

    BigDecimal outstandingBalance;
    BigDecimal outstandingPrincipal;
    BigDecimal outstandingInterest;
    /** Total expected monthly deduction across active loans. */
    BigDecimal monthlyExpectedDeduction;
    LocalDate nextDeductionMonth;

    long missedDeductionCount;
    /** Custom-approval tasks currently assigned to this employee as approver. */
    long assignedApprovalTaskCount;
}
