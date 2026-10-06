package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One installment. Used for both the preview (id and payroll fields null,
 * status PENDING) and the locked schedule after final approval.
 */
@Value
@Builder
public class LoanRepaymentScheduleLineResponse {
    Long id;
    Integer sequenceNumber;
    LocalDate dueMonth;
    BigDecimal expectedAmount;
    BigDecimal principalPortion;
    BigDecimal interestPortion;
    BigDecimal paidAmount;
    BigDecimal outstandingAmount;
    LoanRepaymentStatus status;
    Long payrollRunId;
    Long payrollLineItemId;
    LocalDateTime deductedAt;
    LocalDateTime missedAt;
}
