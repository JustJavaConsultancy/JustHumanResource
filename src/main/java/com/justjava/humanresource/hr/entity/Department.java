package com.justjava.humanresource.hr.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.orgStructure.entity.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "departments")
public class Department extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    /* ===== NEW FIELDS (SAFE EXTENSION) ===== */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    private Department parentDepartment;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    /* ===== Department Head (nullable, single employee) ===== */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_head_id", nullable = true)
    private Employee departmentHead;
}


/* Run this against your DB for Department Code to be generated and run smoothly
    CREATE SEQUENCE department_code_seq
        START WITH 100001
        INCREMENT BY 1;*/

/* Run this against your DB to add the new department_head_id column
    ALTER TABLE departments
        ADD COLUMN department_head_id BIGINT REFERENCES employees(id);*/