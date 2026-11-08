package com.justjava.humanresource.integration.inbound.payroll.entity;

import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertType;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;




@Entity
@Table(name = "integration_alert",
        uniqueConstraints = @UniqueConstraint(name = "uk_integration_alert_dedupe_key", columnNames = "dedupe_key"))
@Getter
@Setter
public class IntegrationAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IntegrationAlertType type;
    @Column(name = "dedupe_key", nullable = false, length = 160)
    private String dedupeKey;
    @Column(name = "journal_reference", length = 80)
    private String journalReference;
    @Column(name = "payroll_period_id")
    private Long payrollPeriodId;
    @Column(nullable = false, length = 500)
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationAlertStatus status;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    @Column(name = "acknowledged_by", length = 160)
    private String acknowledgedBy;
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
}
