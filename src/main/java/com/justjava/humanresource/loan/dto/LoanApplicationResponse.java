package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
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
    LocalDate repaymentStartMonth;
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
