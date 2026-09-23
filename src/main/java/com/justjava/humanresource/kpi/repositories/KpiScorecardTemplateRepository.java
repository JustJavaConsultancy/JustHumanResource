package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiScorecardTemplate;
import com.justjava.humanresource.kpi.enums.KpiScorecardTemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KpiScorecardTemplateRepository extends JpaRepository<KpiScorecardTemplate, Long> {
    List<KpiScorecardTemplate> findByActiveTrueOrderByNameAsc();
    List<KpiScorecardTemplate> findByStatusNotOrderByNameAsc(KpiScorecardTemplateStatus status);
    boolean existsByNameIgnoreCase(String name);
}
