package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjava.humanresource.kpi.enums.KpiFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "kpi_scorecard_template_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiScorecardTemplateItem extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private KpiScorecardTemplate template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private KpiDefinition kpi;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private KpiScorecardTemplateItem parentItem;

    @Column(nullable = false)
    private BigDecimal weight;

    private boolean mandatory;

    private int sortOrder;

    @Enumerated(EnumType.STRING)
    private KpiFrequency frequency;

    @ManyToOne(fetch = FetchType.LAZY)
    private KpiScoringRubric rubric;
}
