package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.kpi.enums.AppraisalOutcome;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "hr_employee_appraisal")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeAppraisal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Employee employee;

    @ManyToOne
    private AppraisalCycle cycle;

    private BigDecimal kpiScore;     // system calculated
    private BigDecimal managerScore; // subjective

    private BigDecimal finalScore;

    @Enumerated(EnumType.STRING)
    private AppraisalOutcome outcome;

    private String managerComment;
    private BigDecimal selfScore;

    @Column(length = 2000)
    private String selfComment;

    private LocalDateTime selfCompletedAt;
    private LocalDateTime completedAt;
}
