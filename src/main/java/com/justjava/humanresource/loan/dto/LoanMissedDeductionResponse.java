package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A flagged missed installment for HR/Finance/employee review. */
@Value
@Builder
public class LoanMissedDeductionResponse {
    Long repaymentScheduleId;
    Long loanAccountId;
    Long loanApplicationId;
    String applicationNumber;
    Long employeeId;
    String employeeName;
    String departmentName;
    String loanProductName;
    Integer sequenceNumber;
    LocalDate dueMonth;
    BigDecimal expectedAmount;
    BigDecimal paidAmount;
    BigDecimal outstandingAmount;
    LoanRepaymentStatus status;
    Long payrollRunId;
    LocalDateTime missedAt;
    /** Remaining balance on the whole loan account. */
    BigDecimal loanOutstandingBalance;
}
