package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.YearMonth;

@Getter
@Setter
@Entity
@Table(
        name = "payroll_periods",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payroll_period",
                columnNames = {"period_year", "period_month"}
        )
)
public class PayrollPeriod extends BaseEntity {

    @Column(name = "period_year", nullable = false)
    private int year;

    @Column(name = "period_month", nullable = false)
    private int month;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollPeriodStatus status;
}
