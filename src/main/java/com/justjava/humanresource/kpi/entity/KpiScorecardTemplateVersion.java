package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kpi_scorecard_template_version")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiScorecardTemplateVersion extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private KpiScorecardTemplate template;

    private int versionNumber;

    @Column(nullable = false)
    private String templateName;

    @Column(length = 1000)
    private String description;

    private String roleName;

    private String publishedBy;

    @Lob
    @Column(nullable = false)
    private String snapshotJson;
}
