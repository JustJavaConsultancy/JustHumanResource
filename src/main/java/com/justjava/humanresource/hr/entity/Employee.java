package com.justjava.humanresource.hr.entity;


import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Table(name = "employees")
public class Employee extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String employeeNumber;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String email;

    private String phoneNumber;

    private LocalDate dateOfHire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentStatus employmentStatus;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @ManyToOne(optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_step_id")
    private JobStep jobStep;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pay_group_id")
    private PayGroup payGroup;

    /** Payroll still independent */
    private boolean payrollEnabled;

    /** KPI readiness flag */
    private boolean kpiEnabled;
    @Column(updatable = false)

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime activatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

}
