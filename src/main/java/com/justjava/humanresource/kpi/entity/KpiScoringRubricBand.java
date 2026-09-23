package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "kpi_scoring_rubric_band")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiScoringRubricBand extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private KpiScoringRubric rubric;

    @Column(nullable = false)
    private String label;

    private BigDecimal minScore;

    private BigDecimal maxScore;

    @Column(nullable = false)
    private BigDecimal numericScore;

    @Column(length = 1000)
    private String description;

    private int sortOrder;
}
