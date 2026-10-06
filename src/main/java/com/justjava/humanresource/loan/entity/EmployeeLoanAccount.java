package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.loan.enums.LoanAccountStatus;
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
        name = "employee_loan_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_loan_account_application", columnNames = "loan_application_id"),
        indexes = {
                @Index(name = "idx_loan_account_employee", columnList = "employee_id"),
                @Index(name = "idx_loan_account_status", columnList = "status")
        }
)
public class EmployeeLoanAccount extends BaseEntity {

    @Column(name = "loan_application_id", nullable = false)
    private Long loanApplicationId;

    @Column(name = "loan_product_id", nullable = false)
    private Long loanProductId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalRepayableAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPaidAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingBalance;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal repaymentAmount;

    @Column(nullable = false)
    private Integer tenorMonths;

    @Column(nullable = false)
    private LocalDate repaymentStartMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanAccountStatus status = LoanAccountStatus.ACTIVE;

    private LocalDateTime activatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime closedAt;
}
