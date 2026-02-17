package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
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
        name = "pay_slips",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payroll_run",
                        columnNames = {"payroll_run_id"}
                )
        }
)
public class PaySlip extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @Column(nullable = false)
    private LocalDate payDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal grossPay;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDeductions;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal netPay;

    private Integer versionNumber;
}
