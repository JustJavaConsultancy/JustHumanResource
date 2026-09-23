package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "kpi_appraisal_line",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_kpi_appraisal_line",
                        columnNames = {"appraisal_id", "kpi_id"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiAppraisalLine extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private EmployeeAppraisal appraisal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private KpiDefinition kpi;

    private BigDecimal weight;

    private BigDecimal measurementScore;

    private BigDecimal selfScore;

    @Column(length = 2000)
    private String selfComment;

    private BigDecimal managerScore;

    @Column(length = 2000)
    private String managerComment;

    @ManyToOne(fetch = FetchType.LAZY)
    private KpiScoringRubric rubric;

    @ManyToOne(fetch = FetchType.LAZY)
    private KpiScoringRubricBand selfRubricBand;

    @ManyToOne(fetch = FetchType.LAZY)
    private KpiScoringRubricBand managerRubricBand;

    private BigDecimal finalScore;

    private BigDecimal weightedScore;

    @Column(length = 1000)
    private String evidenceUrl;
}
