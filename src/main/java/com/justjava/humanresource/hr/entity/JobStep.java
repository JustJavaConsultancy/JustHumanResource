package com.justjava.humanresource.hr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Table(name = "job_steps")
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
