package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KpiScorecardTemplateVersionRepository extends JpaRepository<KpiScorecardTemplateVersion, Long> {
    int countByTemplate_Id(Long templateId);
    List<KpiScorecardTemplateVersion> findByTemplate_IdOrderByVersionNumberDesc(Long templateId);
    Optional<KpiScorecardTemplateVersion> findTopByTemplate_IdOrderByVersionNumberDesc(Long templateId);
}
