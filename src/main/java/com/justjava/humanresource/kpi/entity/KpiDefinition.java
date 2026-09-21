package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.kpi.enums.KpiCategory;
import com.justjava.humanresource.kpi.enums.KpiUnit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "kpi_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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

    @Column(nullable = false)
    private boolean impactSalary = false;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private KpiDefinition parentDefinition;

    @JsonIgnore
    @OneToMany(mappedBy = "parentDefinition")
    @Builder.Default
    private List<KpiDefinition> children = new ArrayList<>();

    @Transient
    public Long getParentDefinitionId() {
        return parentDefinition != null ? parentDefinition.getId() : null;
    }

    @Transient
    public String getParentDefinitionName() {
        return parentDefinition != null ? parentDefinition.getName() : null;
    }

    @Transient
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
}