package com.justjava.humanresource.hr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;


@Table(name = "job_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class JobStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private BigDecimal basicSalary;

    @ManyToOne
    private Department department;

    @ManyToOne
    private JobGrade jobGrade;
}
