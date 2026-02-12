package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "kpi_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiAssignment extends BaseEntity {

    @ManyToOne(optional = false)
    private KpiDefinition kpi;

    @ManyToOne
    private Employee employee; // nullable → role-based KPI

    @ManyToOne
    private JobStep jobStep;

    private BigDecimal weight; // importance (e.g. 0.3)

    private boolean mandatory;
}

