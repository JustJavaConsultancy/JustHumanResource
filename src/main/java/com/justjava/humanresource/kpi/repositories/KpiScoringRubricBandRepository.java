package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiScoringRubricBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KpiScoringRubricBandRepository extends JpaRepository<KpiScoringRubricBand, Long> {
    List<KpiScoringRubricBand> findByRubric_IdOrderBySortOrderAscIdAsc(Long rubricId);
    void deleteByRubric_Id(Long rubricId);
}
