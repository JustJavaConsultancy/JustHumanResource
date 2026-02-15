package com.justjava.humanresource.payroll.entity;


import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.PayGroup;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "pay_group_allowances",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paygroup_allowance_effective",
                columnNames = {
                        "pay_group_id",
                        "allowance_id",
                        "effective_from"
                }
        )
)
public class PayGroupAllowance extends BaseEntity {

    /* =========================
     * RELATIONSHIPS
     * ========================= */

    @ManyToOne(optional = false)
    @JoinColumn(name = "pay_group_id")
    private PayGroup payGroup;

    @ManyToOne(optional = false)
    @JoinColumn(name = "allowance_id")
    private Allowance allowance;

    /* =========================
     * OPTIONAL OVERRIDE AMOUNT
     * ========================= */

    /**
     * If null → use allowance.defaultAmount
     * If present → override group-level amount
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal overrideAmount;

    /* =========================
     * EFFECTIVE DATING
     * ========================= */

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /* =========================
     * STATUS
     * ========================= */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;
}
