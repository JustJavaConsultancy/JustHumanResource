package com.justjava.humanresource.kpi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Entity
@Table(name = "kpi_evaluation_cycle",
        uniqueConstraints = @UniqueConstraint(columnNames = {"period"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiEvaluationCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private YearMonth period;

    private boolean started;

    private boolean completed;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private int totalEmployees;

    private int processedEmployees;
}
