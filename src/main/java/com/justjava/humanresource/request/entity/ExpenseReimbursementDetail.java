package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.ExpensePaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "expense_reimbursement_details")
public class ExpenseReimbursementDetail extends BaseEntity {
    @Column(nullable = false, unique = true) private Long workflowRequestId;
    @Column(nullable = false) private Long claimantEmployeeId;
    private Long departmentId;
    @Column(nullable = false) private LocalDate expenseStartDate;
    @Column(nullable = false) private LocalDate expenseEndDate;
    @Column(nullable = false, length = 2000) private String businessPurpose;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ExpensePaymentMethod paymentMethod;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal totalClaimAmount;
}
