package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanInterestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "employee_loan_applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_loan_application_number", columnNames = "application_number"),
        indexes = {
                @Index(name = "idx_loan_app_employee", columnList = "employee_id"),
                @Index(name = "idx_loan_app_status", columnList = "status"),
                @Index(name = "idx_loan_app_product", columnList = "loan_product_id")
        }
)
public class EmployeeLoanApplication extends BaseEntity {

    @Column(name = "application_number", nullable = false, length = 50)
    private String applicationNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "loan_product_id", nullable = false)
    private LoanProduct loanProduct;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // ----- Employee context snapshots -----
    private Long departmentId;
    private String jobGradeNameSnapshot;
    private String jobStepNameSnapshot;

    @Column(precision = 15, scale = 2)
    private BigDecimal grossSalarySnapshot;

    // ----- Employee-selected terms -----
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal repaymentAmount;

    @Column(nullable = false)
    private Integer tenorMonths;

    /** First day of the selected repayment start month. */
    @Column(nullable = false)
    private LocalDate repaymentStartMonth;

    /**
     * First deduction month actually used at activation. Null until activation.
     * repaymentStartMonth above always remains the employee-selected month.
     * effective = max(selected, month after actual disbursement month).
     */
    private LocalDate effectiveRepaymentStartMonth;

    // ----- Product financial snapshots (set on submission) -----
    @Enumerated(EnumType.STRING)
    private LoanInterestType interestTypeSnapshot;

    @Column(precision = 7, scale = 4)
    private BigDecimal interestRateSnapshot;

    // ----- Approval route snapshots (set on submission) -----
    @Enumerated(EnumType.STRING)
    private LoanApprovalRouteType approvalRouteTypeSnapshot;

    private Long customApprovalPathIdSnapshot;
    private String customApprovalPathNameSnapshot;

    // ----- Disbursement snapshots (set on submission) -----
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private LoanDisbursementMethod disbursementMethodSnapshot;

    /** Bank details captured at submission; only populated for OUTSIDE_PAYROLL. */
    private String bankNameSnapshot;
    private String accountNameSnapshot;

    @Column(length = 20)
    private String accountNumberSnapshot;

    // ----- Calculated totals -----
    @Column(precision = 15, scale = 2)
    private BigDecimal totalRepayableAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalInterestAmount;

    @Column(length = 2000)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanApplicationStatus status = LoanApplicationStatus.DRAFT;

    private String workflowInstanceId;

    // ----- Lifecycle timestamps / actors -----
    private LocalDateTime submittedAt;

    private LocalDateTime hrApprovedAt;
    private Long hrApprovedByEmployeeId;

    private LocalDateTime financeApprovedAt;
    private Long financeApprovedByEmployeeId;

    private LocalDateTime customApprovalCompletedAt;

    /** Set when the last approver (Finance or final custom approver) approves. */
    private LocalDateTime finalApprovedAt;
    private Long finalApprovedByEmployeeId;

    /** Set when an OUTSIDE_PAYROLL loan enters PENDING_DISBURSEMENT. */
    private LocalDateTime disbursementPendingAt;

    private LocalDateTime rejectedAt;
    private Long rejectedByEmployeeId;

    private LocalDateTime cancelledAt;
    private Long cancelledByEmployeeId;

    private LocalDateTime activatedAt;
    private LocalDateTime closedAt;
}