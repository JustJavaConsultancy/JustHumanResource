package com.justjava.humanresource.orgStructure.entity;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.Department;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payroll_routing_configs")
public class PayrollRoutingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payroll_approver_id")
    private Employee payrollApprover;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
