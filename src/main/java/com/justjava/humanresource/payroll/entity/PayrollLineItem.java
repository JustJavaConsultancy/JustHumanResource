package com.justjava.humanresource.payroll.entity;


import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "payroll_line_items")
public class PayrollLineItem extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayComponentType componentType;

    @Column(nullable = false)
    private String componentCode;

    @Column(nullable = false)
    private String description;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean taxable;

}
