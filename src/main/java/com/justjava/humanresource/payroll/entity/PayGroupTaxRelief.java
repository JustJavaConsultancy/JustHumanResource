package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.entity.TaxRelief;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "paygroup_tax_reliefs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_paygroup_tax_relief",
                        columnNames = {"pay_group_id", "tax_relief_id"}
                )
        })
@Getter
@Setter
public class PayGroupTaxRelief {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* =========================
     * RELATIONSHIPS
     * ========================= */

    @ManyToOne(optional = false)
    @JoinColumn(name = "pay_group_id")
    private PayGroup payGroup;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tax_relief_id")
    private TaxRelief taxRelief;

    /* =========================
     * OVERRIDE SUPPORT
     * ========================= */

    @Column(precision = 19, scale = 2)
    private BigDecimal overrideAmount;

    /* =========================
     * EFFECTIVE DATING
     * ========================= */

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    /* =========================
     * STATUS
     * ========================= */
    @Enumerated(EnumType.STRING)
    private RecordStatus status = RecordStatus.ACTIVE;
}