package com.justjava.humanresource.integration.inbound.payroll.entity;

import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshotLine;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;




@Entity
@Table(name = "payroll_journal_snapshot", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payroll_snapshot_active_key", columnNames = "active_key")
})
@Getter
@Setter
public class PayrollJournalSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "journal_reference", nullable = false, length = 80)
    private String journalReference;
    @Column(name = "active_key", length = 80)
    private String activeKey;
    @Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(name = "payroll_period_id", nullable = false)
    private Long payrollPeriodId;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 20)
    private String revision;
    @Column(name = "header_narration", nullable = false, length = 80)
    private String headerNarration;
    @Column(name = "total_debit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebit;
    @Column(name = "total_credit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredit;
    @Column(name = "line_count", nullable = false)
    private int lineCount;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SnapshotStatus status;
    @Column(name = "status_changed_at", nullable = false)
    private LocalDateTime statusChangedAt;
    @Column(name = "ams_journal_id")
    private Long amsJournalId;
    @Column(name = "last_reason_code", length = 100)
    private String lastReasonCode;
    @Column(name = "last_reason_message", length = 500)
    private String lastReasonMessage;
    @Column(name = "void_reason", length = 250)
    private String voidReason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<PayrollJournalSnapshotLine> lines = new ArrayList<>();
}
