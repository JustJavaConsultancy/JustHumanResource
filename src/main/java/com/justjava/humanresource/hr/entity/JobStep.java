package com.justjava.humanresource.hr.entity;


import com.justjava.humanresource.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "job_steps")
public class JobStep extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_grade_id")
    private JobGrade jobGrade;

    @Column(nullable = false)
    private Integer stepLevel;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal baseSalary;
}
