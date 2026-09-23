package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "kpi_template_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiTemplateAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private KpiScorecardTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    private KpiScorecardTemplateVersion templateVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    private JobStep jobStep;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    private LocalDate validFrom;

    private LocalDate validTo;

    private boolean active;

    private String appliedBy;
}
