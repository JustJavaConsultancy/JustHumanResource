package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "payroll_journal_entries")
public class PayrollJournalEntry extends BaseEntity {

    @Column(nullable = false)
    private Long payrollPeriodId;

    @Column(nullable = false)
    private String accountCode;

    @Column(nullable = false)
    private String accountName;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal debitAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal creditAmount;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean exported = false;
}
