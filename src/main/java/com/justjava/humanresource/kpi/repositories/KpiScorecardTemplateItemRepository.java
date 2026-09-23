package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KpiScorecardTemplateItemRepository extends JpaRepository<KpiScorecardTemplateItem, Long> {
    @Query("""
           SELECT i
           FROM KpiScorecardTemplateItem i
           JOIN FETCH i.kpi
           LEFT JOIN FETCH i.parentItem
           WHERE i.template.id = :templateId
           ORDER BY i.sortOrder ASC, i.id ASC
           """)
    List<KpiScorecardTemplateItem> findByTemplate_IdOrderBySortOrderAscIdAsc(@Param("templateId") Long templateId);
    void deleteByTemplate_Id(Long templateId);
}
