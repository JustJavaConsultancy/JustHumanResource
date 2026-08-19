package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "payroll_journal_batches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payroll_journal_batch_company_period",
                columnNames = {"company_id", "payroll_period_id"}
        )
)
public class PayrollJournalBatch extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "payroll_period_id", nullable = false)
    private Long payrollPeriodId;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean balanced;

    @Column(nullable = false)
    private boolean exported = false;

    private LocalDateTime exportedAt;

    private String exportedBy;

    private String exportFormat;

    private String exportReference;
}
