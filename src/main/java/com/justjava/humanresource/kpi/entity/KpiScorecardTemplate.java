package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjava.humanresource.kpi.enums.KpiScorecardTemplateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "kpi_scorecard_template")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiScorecardTemplate extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    private String roleName;

    private BigDecimal totalWeight;

    private boolean active;

    private String sourceType;

    private String sourceFileName;

    private String importedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private KpiScorecardTemplateStatus status = KpiScorecardTemplateStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    private KpiScoringRubric defaultRubric;

    @JsonIgnore
    @OneToMany(mappedBy = "template")
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<KpiScorecardTemplateItem> items = new ArrayList<>();
}
