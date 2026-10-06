package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanRepaymentTransactionType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One actual payroll deduction recorded against a loan. */
@Value
@Builder
public class LoanRepaymentTransactionResponse {
    Long id;
    Long loanAccountId;
    Long loanApplicationId;
    String applicationNumber;
    Long repaymentScheduleId;
    Integer scheduleSequenceNumber;
    Long employeeId;
    Long payrollRunId;
    Long payrollLineItemId;
    BigDecimal amount;
    LocalDate transactionMonth;
    LoanRepaymentTransactionType transactionType;
    LocalDateTime createdAt;
}
