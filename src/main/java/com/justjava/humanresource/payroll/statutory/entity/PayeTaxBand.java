package com.justjava.humanresource.payroll.statutory.entity;


import com.justjava.humanresource.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "paye_tax_bands")
public class PayeTaxBand extends BaseEntity {

    @Column(nullable = false)
    private BigDecimal lowerBound;

    @Column(nullable = false)
    private BigDecimal upperBound;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;
}
