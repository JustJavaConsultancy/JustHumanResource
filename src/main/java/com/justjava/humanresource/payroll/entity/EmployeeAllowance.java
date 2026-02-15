package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "employee_allowances",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_employee_allowance_effective",
                columnNames = {
                        "employee_id",
                        "allowance_id",
                        "effective_from"
                }
        )
)
public class EmployeeAllowance extends BaseEntity {

    /* =========================
     * RELATIONSHIPS
     * ========================= */

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(optional = false)
    @JoinColumn(name = "allowance_id")
    private Allowance allowance;

    /* =========================
     * OVERRIDE CONTROL
     * ========================= */

    /**
     * If true → use overrideAmount
     * If false → use allowance.defaultAmount
     */
    @Column(nullable = false)
    private boolean overridden = false;

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
