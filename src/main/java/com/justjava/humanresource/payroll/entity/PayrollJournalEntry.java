package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "payroll_journal_entries")
public class PayrollJournalEntry extends BaseEntity {


    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long payrollPeriodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_batch_id")
    private PayrollJournalBatch journalBatch;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

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

    private String sourceType;

    private String sourceCode;

    private String sourceName;

    private String lineType;

    private Long departmentId;

    private String departmentName;

    private Long payGroupId;

    private String payGroupName;

    @Column(nullable = false)
    private boolean exported = false;

    private LocalDateTime exportedAt;

    private String exportedBy;
}
