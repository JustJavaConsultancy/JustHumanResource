package com.justjava.humanresource.kpi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.YearMonth;

@Entity
@Table(name = "hr_appraisal_cycle")
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

    private YearMonth period;

    private boolean active;
}
