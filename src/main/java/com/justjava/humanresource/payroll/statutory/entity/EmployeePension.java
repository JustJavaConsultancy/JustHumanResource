package com.justjava.humanresource.payroll.statutory.entity;


import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Employee;
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
        name = "employee_pensions",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"employee_id", "pension_scheme_id"}
        )
)
public class EmployeePension extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pension_scheme_id")
    private PensionScheme pensionScheme;
}
