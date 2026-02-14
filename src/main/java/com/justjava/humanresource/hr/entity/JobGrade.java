package com.justjava.humanresource.hr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Table(name = "job_grades")
@Entity
public class JobGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    private Department department;

    @OneToMany(mappedBy = "jobGrade", cascade = CascadeType.ALL)
    private List<JobStep> jobSteps;
}

/*

{
        "gradeName": "Grade A",
        "departmentId": 1,
        "steps": [
        {
        "stepName": "Step 1",
        "basicSalary": 150000
        },
        {
        "stepName": "Step 2",
        "basicSalary": 180000
        },
        {
        "stepName": "Step 3",
        "basicSalary": 210000
        }
        ]
        }
*/
