package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppraisalCycleRepository
        extends JpaRepository<AppraisalCycle, Long> {

    /* =====================================================
       UNIQUE QUARTER IDENTIFIER
       ===================================================== */

    Optional<AppraisalCycle> findByYearAndQuarter(
            int year,
            int quarter
    );

    boolean existsByYearAndQuarter(
            int year,
            int quarter
    );

    /* =====================================================
       ACTIVE CYCLE
       ===================================================== */

    Optional<AppraisalCycle> findByActiveTrue();

    /* =====================================================
       REPORTING
       ===================================================== */

    List<AppraisalCycle> findByYear(int year);

    List<AppraisalCycle> findByCompleted(boolean completed);

    /* =====================================================
       RANGE LOOKUP (Useful for dashboards)
       ===================================================== */

    List<AppraisalCycle> findByStartPeriodBetween(
            YearMonth start,
            YearMonth end
    );
}
