package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanInterestType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Complete application record. Returned from create/update/submit/cancel calls. */
@Value
@Builder
public class LoanApplicationResponse {
    Long id;
    String applicationNumber;
    LoanApplicationStatus status;

    Long loanProductId;
    String loanProductCode;
    String loanProductName;

    Long employeeId;
    String employeeName;
    Long departmentId;
    String departmentName;
    String jobGradeName;
    String jobStepName;
    BigDecimal grossSalary;

    BigDecimal requestedAmount;
    BigDecimal repaymentAmount;
    Integer tenorMonths;
    /** Employee-selected first repayment month. */
    LocalDate repaymentStartMonth;
    /** First deduction month actually used at activation; null until the loan is activated. */
    LocalDate effectiveRepaymentStartMonth;
    LoanInterestType interestType;
    BigDecimal interestRate;
    BigDecimal totalInterestAmount;
    BigDecimal totalRepayableAmount;
    String purpose;

    // Route snapshot
    LoanApprovalRouteType approvalRouteType;
    String approvalRouteLabel;
    Long customApprovalPathId;
    String customApprovalPathName;

    // Disbursement
    LoanDisbursementMethod disbursementMethod;
    String disbursementMethodLabel;
    /** True when the method is OUTSIDE_PAYROLL, i.e. complete bank details are mandatory. */
    boolean bankDetailsRequired;
    /**
     * True when the bank-details requirement is satisfied: always true when not required,
     * otherwise true only if the bank details are complete.
     */
    boolean bankDetailsComplete;
    /**
     * OUTSIDE_PAYROLL only (null otherwise). DRAFT / RETURNED_FOR_CORRECTION show the employee's current
     * bank details; every later status shows the snapshot taken at submission.
     */
    LoanBankDetailResponse bankDetails;

    String workflowInstanceId;

    // Lifecycle
    LocalDateTime submittedAt;
    LocalDateTime hrApprovedAt;
    Long hrApprovedByEmployeeId;
    LocalDateTime financeApprovedAt;
    Long financeApprovedByEmployeeId;
    LocalDateTime customApprovalCompletedAt;
    LocalDateTime rejectedAt;
    Long rejectedByEmployeeId;
    LocalDateTime cancelledAt;
    Long cancelledByEmployeeId;
    LocalDateTime activatedAt;
    LocalDateTime closedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
