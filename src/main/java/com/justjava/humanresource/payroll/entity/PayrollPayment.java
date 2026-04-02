package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.payroll.dto.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payroll_payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payroll_payment_run",
                        columnNames = {"payroll_run_id"}
                )
        }
)
@Getter
@Setter
public class PayrollPayment extends BaseEntity {

    private Long payrollRunId;
    private Long employeeId;
    private Long companyId;

    private BigDecimal amount;

    private String bankName;
    private String accountNumber;
    private String accountName;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status; // PENDING, PROCESSING, SUCCESS, FAILED

    private String externalReference;
    private String failureReason;
    @Column(nullable = false)
    private int retryCount = 0;

    @Column
    private LocalDateTime lastTriedAt;
    @Column
    private LocalDateTime nextRetryAt;
    @Column
    private String processInstanceId;
}