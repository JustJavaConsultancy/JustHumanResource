package com.justjava.humanresource.payroll.entity;


import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "payroll_audit_logs")
public class PayrollAuditLog extends BaseEntity {

    @Column(nullable = false)
    private String entityType;   // PayrollPeriod, PayrollRun

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private String action;       // CLOSE, REOPEN, APPROVE, EXPORT

    @Column(nullable = false)
    private String performedBy;

    @Column(nullable = false)
    private String performedRole;

    @Column(nullable = false)
    private LocalDateTime performedAt;

    @Column(length = 2000)
    private String details;
}
