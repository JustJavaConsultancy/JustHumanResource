package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.common.entity.BaseEntity;
import com.justjava.humanresource.common.enums.PayrollRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "payroll_runs")
public class PayrollRun extends BaseEntity {

    @Column(nullable = false)
    private LocalDate payrollDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollRunStatus status;

    @Column(nullable = false)
    private String flowableProcessInstanceId;
}
