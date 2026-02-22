package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.payroll.enums.PayrollCycleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payroll_cycle_config")
public class PayrollCycleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long companyId;

    @Enumerated(EnumType.STRING)
    private PayrollCycleType cycleType;

    /*
     * Used for FIXED_DAY_OF_MONTH
     * e.g. 21 means cycle ends on 21st
     */
    private Integer cutOffDay;

    /*
     * Used for CUSTOM_DAY_SPAN
     */
    private Integer cycleLengthDays;

    private boolean active;
}