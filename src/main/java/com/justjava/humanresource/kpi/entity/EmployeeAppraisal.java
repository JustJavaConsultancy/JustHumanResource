package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.kpi.enums.AppraisalOutcome;
import com.justjava.humanresource.kpi.enums.AppraisalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "hr_employee_appraisal",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_employee_cycle",
                        columnNames = {"employee_id", "cycle_id"}
                )

        },
        indexes = {
                @Index(name = "idx_appraisal_employee", columnList = "employee_id"),
                @Index(name = "idx_appraisal_cycle", columnList = "cycle_id"),
                @Index(name = "idx_appraisal_completed", columnList = "completedAt")
        }
)
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

    @Enumerated(EnumType.STRING)
    private AppraisalStatus status;

    private LocalDateTime selfCompletedAt;
    private LocalDateTime completedAt;
}
