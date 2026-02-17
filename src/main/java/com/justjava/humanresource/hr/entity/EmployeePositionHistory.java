package com.justjava.humanresource.hr.entity;

import com.justjava.humanresource.core.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;


@Entity
@Table(
        name = "employee_position_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_employee_position_effective",
                columnNames = {
                        "employee_id",
                        "effective_from"
                }
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeePositionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Employee employee;

    @ManyToOne(optional = false)
    private Department department;

    @ManyToOne(optional = false)
    private JobStep jobStep;

    @ManyToOne(optional = false)
    private PayGroup payGroup;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private boolean current;
    private RecordStatus status;
}