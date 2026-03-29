package com.justjava.humanresource.payroll.entity;


import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.payroll.enums.PayComponentCalculationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "tax_reliefs")
public class TaxRelief extends BaseEntity {

    /* =========================
     * IDENTIFICATION
     * ========================= */

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    /* =========================
     * CALCULATION
     * ========================= */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayComponentCalculationType calculationType = PayComponentCalculationType.FIXED_AMOUNT;

    /*
     * Used when FIXED_AMOUNT
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    /*
     * Used when percentage-based
     */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentageRate;

    /*
     * Used when FORMULA
     */
    @Column(length = 1000)
    private String formulaExpression;

    /* =========================
     * CONTROL FLAGS
     * ========================= */

    @Column(nullable = false)
    private boolean active = true;

    /*
     * Optional: cap for relief
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal maximumAmount;
}