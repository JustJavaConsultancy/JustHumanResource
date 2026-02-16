package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiEvaluationCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.Optional;

public interface KpiEvaluationCycleRepository
        extends JpaRepository<KpiEvaluationCycle, Long> {
    Optional<KpiEvaluationCycle> findByPeriod(YearMonth period);
}
