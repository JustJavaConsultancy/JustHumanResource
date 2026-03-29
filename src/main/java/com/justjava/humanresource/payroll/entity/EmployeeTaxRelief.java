package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employee_tax_reliefs")
@Getter
@Setter
public class EmployeeTaxRelief {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;

    @ManyToOne(optional = false)
    private TaxRelief taxRelief;

    private boolean overridden;

    @Column(precision = 19, scale = 2)
    private BigDecimal overrideAmount;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    private RecordStatus status = RecordStatus.ACTIVE;
}