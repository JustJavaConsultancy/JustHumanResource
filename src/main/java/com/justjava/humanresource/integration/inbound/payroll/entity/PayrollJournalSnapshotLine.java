package com.justjava.humanresource.integration.inbound.payroll.entity;

import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshot;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;




@Entity
@Immutable
@Table(name = "payroll_journal_snapshot_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payroll_snapshot_line_number",
                columnNames = {"snapshot_id", "line_number"}))
@Getter
@Setter
public class PayrollJournalSnapshotLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PayrollJournalSnapshot snapshot;
    @Column(name = "line_number", nullable = false)
    private int lineNumber;
    @Column(name = "account_code", nullable = false, length = 100)
    private String accountCode;
    @Column(name = "account_name", nullable = false, length = 250)
    private String accountName;
    @Column(name = "line_type", length = 50)
    private String lineType;
    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal debitAmount;
    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditAmount;
    @Column(length = 80)
    private String narration;

    @PreUpdate
    @PreRemove
    private void rejectMutation() {
        throw new IllegalStateException("Payroll journal snapshot lines are immutable.");
    }
}
