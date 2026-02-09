package com.justjava.humanresource.payroll.entity;


import com.justjava.humanresource.common.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "employee_deductions",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"employee_id", "deduction_id"}
        )
)
public class EmployeeDeduction extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(optional = false)
    @JoinColumn(name = "deduction_id")
    private Deduction deduction;

    @Column(nullable = false)
    private boolean overridden;
}
