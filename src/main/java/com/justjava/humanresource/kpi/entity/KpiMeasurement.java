package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Employee;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Entity
@Table(name = "kpi_measurement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiMeasurement extends BaseEntity {

    @ManyToOne(optional = false)
    private Employee employee;

    @ManyToOne(optional = false)
    private KpiDefinition kpi;

    private BigDecimal actualValue;

    private BigDecimal score; // normalized (0–100)

    private YearMonth period;

    private LocalDateTime recordedAt;
}

