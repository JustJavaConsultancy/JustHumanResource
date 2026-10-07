package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Table row for employee, HR, and Finance application/active-loan lists. */
@Value
@Builder
public class LoanApplicationSummaryResponse {
    Long id;
    String applicationNumber;
    LoanApplicationStatus status;

    Long employeeId;
    String employeeName;
    String departmentName;

    Long loanProductId;
    String loanProductName;

    BigDecimal requestedAmount;
    BigDecimal repaymentAmount;
    Integer tenorMonths;
    LocalDate repaymentStartMonth;
    /** First deduction month actually used at activation; null until the loan is activated. */
    LocalDate effectiveRepaymentStartMonth;

    LoanDisbursementMethod disbursementMethod;
    String disbursementMethodLabel;
    boolean bankDetailsRequired;

    LoanApprovalRouteType approvalRouteType;
    String approvalRouteLabel;
    /** Who the application is waiting on, e.g. "HR group" or a named approver. */
    String currentApprovalOwner;
    Long currentApproverEmployeeId;

    // Populated once the loan is active
    Long loanAccountId;
    BigDecimal outstandingBalance;
    boolean hasMissedDeductions;

    LocalDateTime submittedAt;
    LocalDateTime activatedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
