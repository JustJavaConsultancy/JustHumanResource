package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanAccountStatus;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** An employee's current loan exposure; decision support only, never auto-blocks. */
@Value
@Builder
public class EmployeeLoanExposureResponse {
    Long employeeId;
    String employeeName;
    int activeLoanCount;
    int pendingApplicationCount;
    int missedDeductionCount;
    BigDecimal totalOutstandingBalance;
    /** Sum of repaymentAmount across active loans. */
    BigDecimal totalMonthlyDeduction;
    /** True when there are active loans, pending applications, or unsettled balances. */
    boolean hasExistingExposure;

    @Builder.Default List<ActiveLoanLine> activeLoans = new ArrayList<>();
    @Builder.Default List<PendingLoanLine> pendingApplications = new ArrayList<>();

    @Value
    @Builder
    public static class ActiveLoanLine {
        Long loanAccountId;
        Long loanApplicationId;
        String applicationNumber;
        String loanProductName;
        BigDecimal principalAmount;
        BigDecimal totalRepayableAmount;
        BigDecimal totalPaidAmount;
        BigDecimal outstandingBalance;
        BigDecimal repaymentAmount;
        Integer tenorMonths;
        LocalDate repaymentStartMonth;
        LoanAccountStatus status;
        int missedDeductionCount;
    }

    @Value
    @Builder
    public static class PendingLoanLine {
        Long loanApplicationId;
        String applicationNumber;
        String loanProductName;
        BigDecimal requestedAmount;
        BigDecimal repaymentAmount;
        Integer tenorMonths;
        LoanApplicationStatus status;
    }
}
