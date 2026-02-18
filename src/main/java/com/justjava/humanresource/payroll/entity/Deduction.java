package com.justjava.humanresource.payroll.entity;


import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.enums.PayComponentCalculationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "deductions")
public class Deduction extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean statutory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayComponentCalculationType calculationType = PayComponentCalculationType.FIXED_AMOUNT;

    @Column(precision = 10, scale = 4)
    private BigDecimal percentageRate;

    @Column(length = 1000)
    private String formulaExpression;

    @Column(nullable = false)
    private boolean proratable = false;
}
