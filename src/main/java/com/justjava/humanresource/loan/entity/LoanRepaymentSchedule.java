package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
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
        name = "loan_repayment_schedules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_loan_schedule_account_seq",
                columnNames = {"loan_account_id", "sequence_number"}),
        indexes = {
                @Index(name = "idx_loan_schedule_due", columnList = "due_month, status"),
                @Index(name = "idx_loan_schedule_account", columnList = "loan_account_id")
        }
)
public class LoanRepaymentSchedule extends BaseEntity {

    @Column(name = "loan_account_id", nullable = false)
    private Long loanAccountId;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    /** First day of the due month. */
    @Column(name = "due_month", nullable = false)
    private LocalDate dueMonth;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal expectedAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principalPortion;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interestPortion = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanRepaymentStatus status = LoanRepaymentStatus.PENDING;

    private Long payrollRunId;
    private Long payrollLineItemId;
    private LocalDateTime deductedAt;
    private LocalDateTime missedAt;
}
