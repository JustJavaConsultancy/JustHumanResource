package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.common.entity.BaseEntity;
import com.justjava.humanresource.kpi.enums.KpiCategory;
import com.justjava.humanresource.kpi.enums.KpiUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "kpi_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiDefinition extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String code;

    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    private KpiCategory category; // PRODUCTIVITY, ATTENDANCE, QUALITY

    private BigDecimal targetValue;

    @Enumerated(EnumType.STRING)
    private KpiUnit unit; // PERCENTAGE, NUMBER, HOURS

    private boolean active;
}

