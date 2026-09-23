package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "kpi_scoring_rubric")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiScoringRubric extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    private boolean active;

    @JsonIgnore
    @OneToMany(mappedBy = "rubric")
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<KpiScoringRubricBand> bands = new ArrayList<>();
}
