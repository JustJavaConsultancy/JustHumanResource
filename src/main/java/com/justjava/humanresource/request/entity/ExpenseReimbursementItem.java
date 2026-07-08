package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.ExpenseCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "expense_reimbursement_items")
public class ExpenseReimbursementItem extends BaseEntity {
    @Column(nullable = false) private Long workflowRequestId;
    @Column(nullable = false) private LocalDate expenseDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ExpenseCategory expenseCategory;
    @Column(nullable = false, length = 1000) private String description;
    private String vendorName;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(length = 1000) private String remarks;
}
