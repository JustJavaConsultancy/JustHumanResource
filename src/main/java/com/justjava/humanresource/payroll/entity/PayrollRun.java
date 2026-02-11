package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.common.entity.BaseEntity;
import com.justjava.humanresource.common.enums.PayrollRunStatus;
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
        name = "payroll_runs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_employee_payroll_date",
                        columnNames = {"employee_id", "payroll_date"}
                )
        }
)
public class PayrollRun extends BaseEntity {

    /* =========================
     * CORE RELATIONSHIP
     * ========================= */

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /* =========================
     * PAYROLL PERIOD
     * ========================= */

    @Column(name = "payroll_date", nullable = false)
    private LocalDate payrollDate;

    /*
     * Optional: supports period-based payroll later
     */
    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    /* =========================
     * STATUS MANAGEMENT
     * ========================= */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollRunStatus status;

    /* =========================
     * FLOWABLE TRACEABILITY
     * ========================= */

    @Column(name = "flowable_process_instance_id", nullable = false)
    private String flowableProcessInstanceId;

    /*
     * Business key used for per-employee supervisor process
     */
    @Column(name = "flowable_business_key")
    private String flowableBusinessKey;

    /* =========================
     * SUMMARY SNAPSHOT (IMMUTABLE AFTER POSTED)
     * ========================= */

    @Column(precision = 19, scale = 2)
    private BigDecimal grossPay = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal netPay = BigDecimal.ZERO;

}