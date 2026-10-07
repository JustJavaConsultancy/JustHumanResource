package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanDisbursementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDisbursementResponse {

    private Long id;

    private Long loanApplicationId;
    private String applicationNumber;
    private Long loanAccountId;

    private Long employeeId;
    private String employeeNumber;
    private String employeeName;
    private String department;

    private Long loanProductId;
    private String loanProductName;

    private LoanDisbursementMethod method;
    private String methodLabel;
    private LoanDisbursementStatus status;
    private String statusLabel;

    private BigDecimal amount;

    private String bankName;
    private String accountName;
    private String accountNumber;

    private String approvalRoute;
    private LocalDateTime finalApprovedAt;
    private Long finalApprovedByEmployeeId;

    private LocalDate selectedRepaymentStartMonth;
    /** Null until the loan is activated. */
    private LocalDate effectiveRepaymentStartMonth;
    private LocalDate disbursementMonth;
    /** True when the system moved the first deduction later than the employee's selection. */
    private boolean repaymentStartAdjusted;

    private Long payrollPeriodId;
    private Long payrollRunId;
    private Long payrollLineItemId;

    private LocalDateTime paidAt;
    private Long paidByEmployeeId;
    private String paidByName;
    private String paymentReference;
    private String paymentComment;
}
