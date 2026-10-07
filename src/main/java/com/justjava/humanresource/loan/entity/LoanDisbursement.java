package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanDisbursementStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One disbursement per approved loan application. Source for the Finance queue,
 * Finance history and CSV export, and the audit link between final approval,
 * payment, payroll line and activation.
 *
 * Ids are stored as plain Long (not entity relations) to keep the loan module
 * decoupled from payroll entities.
 */
@Getter
@Setter
@Entity
@Table(
        name = "loan_disbursements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_loan_disbursement_application",
                columnNames = "loan_application_id"),
        indexes = {
                @Index(name = "idx_loan_disbursement_status", columnList = "status"),
                @Index(name = "idx_loan_disbursement_method_status", columnList = "method, status"),
                @Index(name = "idx_loan_disbursement_employee", columnList = "employee_id")
        }
)
public class LoanDisbursement extends BaseEntity {

    @Column(name = "loan_application_id", nullable = false)
    private Long loanApplicationId;

    /** Set once the loan account exists (at activation). */
    @Column(name = "loan_account_id")
    private Long loanAccountId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "loan_product_id", nullable = false)
    private Long loanProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    private LoanDisbursementMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoanDisbursementStatus status;

    /** Approved principal. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Month the employee chose on the application (first day of month). */
    private LocalDate selectedRepaymentStartMonth;

    /** First deduction month actually used at activation (first day of month). */
    private LocalDate effectiveRepaymentStartMonth;

    /** Month the money was actually disbursed (first day of month). */
    private LocalDate disbursementMonth;

    // ----- Payroll mapping (PAYROLL_PERIOD only) -----
    private Long payrollPeriodId;
    private Long payrollRunId;
    private Long payrollLineItemId;

    // ----- Bank snapshot (OUTSIDE_PAYROLL) -----
    private String bankNameSnapshot;
    private String accountNameSnapshot;

    @Column(length = 20)
    private String accountNumberSnapshot;

    // ----- Final approval -----
    private LocalDateTime finalApprovedAt;
    private Long finalApprovedByEmployeeId;

    // ----- Payment confirmation -----
    private LocalDateTime paidAt;
    private Long paidByEmployeeId;

    @Column(length = 100)
    private String paymentReference;

    @Column(length = 2000)
    private String paymentComment;
}
