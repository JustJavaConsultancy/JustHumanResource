package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
@Getter
@Setter
@Entity
@Table(name = "payroll_periods",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_period_range",
                        columnNames = {"company_id", "period_start", "period_end"}
                )
        })
public class PayrollPeriod extends BaseEntity {

    /* =========================
     * COMPANY (Multi-Company Support)
     * ========================= */

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /* =========================
     * PERIOD RANGE
     * ========================= */

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /* =========================
     * STATUS
     * ========================= */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollPeriodStatus status;

    /* =========================
     * CYCLE CONFIG SNAPSHOT
     * ========================= */

    @Column(name = "cycle_length_days")
    private Integer cycleLengthDays;
}

