package com.justjava.humanresource.payroll.statutory.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "paye_tax_bands",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tax_band_unique",
                        columnNames = {"lower_bound", "effective_from"}
                )
        }
)
public class PayeTaxBand extends BaseEntity {

    /* =========================
     * TAX RANGE
     * ========================= */

    @Column(name = "lower_bound", nullable = false, precision = 19, scale = 2)
    private BigDecimal lowerBound;

    /*
     * Nullable to support open-ended top band
     */
    @Column(name = "upper_bound", precision = 19, scale = 2)
    private BigDecimal upperBound;

    /*
     * Example: 7.50 (%)
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;

    /* =========================
     * EFFECTIVE DATING (RETRO SAFE)
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

    /* =========================
     * OPTIONAL REGIME GROUPING
     * ========================= */

    @Column(name = "regime_code")
    private String regimeCode;
}
