package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row of an HR, Finance or custom-approver task queue. */
@Value
@Builder
public class LoanApprovalTaskResponse {
    String taskId;
    String taskName;
    LoanApprovalStage stage;
    LocalDateTime taskCreatedAt;

    Long loanApplicationId;
    String applicationNumber;
    LoanApplicationStatus applicationStatus;
    Long employeeId;
    String employeeName;
    String departmentName;
    String loanProductName;
    BigDecimal requestedAmount;
    BigDecimal repaymentAmount;
    Integer tenorMonths;
    LocalDate repaymentStartMonth;
    LocalDateTime submittedAt;
}
