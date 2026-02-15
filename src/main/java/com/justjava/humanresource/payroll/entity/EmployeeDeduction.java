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
        name = "employee_deductions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_employee_deduction_effective",
                columnNames = {
                        "employee_id",
                        "deduction_id",
                        "effective_from"
                }
        )
)
public class EmployeeDeduction extends BaseEntity {

    /* =========================
     * RELATIONSHIPS
     * ========================= */

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(optional = false)
    @JoinColumn(name = "deduction_id")
    private Deduction deduction;

    /* =========================
     * OVERRIDE CONTROL
     * ========================= */

    /**
     * If true → use overrideAmount
     * If false → use deduction.defaultAmount
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
     * STATUS CONTROL
     * ========================= */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;
}
