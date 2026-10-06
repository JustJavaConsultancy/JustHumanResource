package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.loan.enums.LoanRepaymentTransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "loan_repayment_transactions",
        indexes = {
                @Index(name = "idx_loan_txn_account", columnList = "loan_account_id"),
                @Index(name = "idx_loan_txn_schedule", columnList = "repayment_schedule_id"),
                @Index(name = "idx_loan_txn_payroll_item", columnList = "payroll_line_item_id")
        }
)
public class LoanRepaymentTransaction extends BaseEntity {

    @Column(name = "loan_account_id", nullable = false)
    private Long loanAccountId;

    @Column(name = "repayment_schedule_id", nullable = false)
    private Long repaymentScheduleId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "payroll_run_id")
    private Long payrollRunId;

    @Column(name = "payroll_line_item_id")
    private Long payrollLineItemId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate transactionMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanRepaymentTransactionType transactionType = LoanRepaymentTransactionType.PAYROLL_DEDUCTION;
}
