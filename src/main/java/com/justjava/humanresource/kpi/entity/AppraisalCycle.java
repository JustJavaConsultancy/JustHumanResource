package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.payroll.util.YearMonthAttributeConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppraisalCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // e.g. 2026 Q1

    private int year;

    private int quarter; // 1 - 4

    @Convert(converter = YearMonthAttributeConverter.class)
    private YearMonth startPeriod; // e.g. 2026-01

    @Convert(converter = YearMonthAttributeConverter.class)
    private YearMonth endPeriod;   // e.g. 2026-03

    private boolean active;

    private boolean completed;

    private int totalEmployees;

    private int processedEmployees;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
