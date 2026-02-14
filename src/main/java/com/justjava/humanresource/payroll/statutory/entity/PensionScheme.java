package com.justjava.humanresource.payroll.statutory.entity;


import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import jakarta.persistence.*;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "pension_schemes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pension_code_effective",
                        columnNames = {"code", "effective_from"}
                )
        }
)
public class PensionScheme extends BaseEntity {

    /* =========================
     * IDENTIFICATION
     * ========================= */

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    /* =========================
     * CONTRIBUTION RATES
     * ========================= */

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal employeeRate;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal employerRate;

    /* =========================
     * PENSIONABLE BASE CONTROL
     * ========================= */

    /**
     * If true → pension applies only to basic salary.
     * If false → applies to gross earnings.
     */
    @Column(nullable = false)
    private Boolean pensionableOnBasicOnly = true;

    /**
     * Optional cap on pensionable amount.
     * Null means no cap.
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal pensionableCap;

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
