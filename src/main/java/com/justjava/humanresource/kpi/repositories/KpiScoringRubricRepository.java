package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiScoringRubric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KpiScoringRubricRepository extends JpaRepository<KpiScoringRubric, Long> {
    List<KpiScoringRubric> findByActiveTrueOrderByNameAsc();
}
